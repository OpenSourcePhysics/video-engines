package org.opensourcephysics.media.ffmpeg;

import static org.ffmpeg.avcodec.AvcodecLibrary.av_free_packet;
import static org.ffmpeg.avcodec.AvcodecLibrary.av_init_packet;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_decode_video2;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_find_decoder;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_open2;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_flush_buffers;
import static org.ffmpeg.avformat.AvformatLibrary.av_read_frame;
import static org.ffmpeg.avformat.AvformatLibrary.avformat_find_stream_info;
import static org.ffmpeg.avformat.AvformatLibrary.avformat_open_input;
import static org.ffmpeg.avutil.AvutilLibrary.AV_NOPTS_VALUE;
import static org.ffmpeg.avutil.AvutilLibrary.av_frame_get_best_effort_timestamp;
import static org.ffmpeg.avutil.AvutilLibrary.av_freep;
import static org.ffmpeg.avutil.AvutilLibrary.av_image_copy;
import static org.ffmpeg.avutil.AVUtil.av_q2d;
import static org.opensourcephysics.media.ffmpeg.FFMPegAnalyzer.copy;
import static org.opensourcephysics.media.ffmpeg.FFMPegAnalyzer.isKeyFrame;
import static org.opensourcephysics.media.ffmpeg.FFMPegAnalyzer.isVideoPacket;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.bridj.Pointer;
import org.ffmpeg.avcodec.AVCodec;
import org.ffmpeg.avcodec.AVCodecContext;
import org.ffmpeg.avcodec.AVPacket;
import org.ffmpeg.avcodec.AvcodecLibrary;
import org.ffmpeg.avformat.AVFormatContext;
import org.ffmpeg.avformat.AVStream;
import org.ffmpeg.avformat.AvformatLibrary;
import org.ffmpeg.avutil.AVFrame;
import org.ffmpeg.avutil.AVRational;
import org.ffmpeg.avutil.AvutilLibrary;
import org.opensourcephysics.controls.OSPLog;
import org.opensourcephysics.controls.XML;
import org.opensourcephysics.controls.XMLControl;
import org.opensourcephysics.media.core.DoubleArray;
import org.opensourcephysics.media.core.Filter;
import org.opensourcephysics.media.core.ImageCoordSystem;
import org.opensourcephysics.media.core.VideoAdapter;
import org.opensourcephysics.media.core.VideoIO;
import org.opensourcephysics.media.core.VideoType;
import org.opensourcephysics.tools.Resource;
import org.opensourcephysics.tools.ResourceLoader;

/**
 * A class to display videos using the ffmpeg library.
 */
public class FFMPegVideo extends VideoAdapter {

	Pointer<AVFormatContext> context;
	int streamIndex = -1;
	Pointer<AVCodecContext> cContext;
	Pointer<AVCodec> codec;
	Pointer<AVFrame> frame;
	Pointer<AVPacket> packet;
	Pointer<Pointer<Byte>> picture = Pointer.allocatePointers(Byte.class, 4);
	Pointer<Integer> picture_linesize = Pointer.allocateInts(4);
	int picture_bufsize;
	Pointer<AVStream> stream;
	AVRational timebase;
	BgrConverter converter;
	// maps frame number to timestamp of displayed frame (last frame loaded)
	Map<Integer, Long> frameTimeStamps = new HashMap<Integer, Long>();
	// maps frame number to timestamp of key frame (first frame loaded)
	Map<Integer, Long> keyTimeStamps = new HashMap<Integer, Long>();
	// array of frame start times in milliseconds
	private double[] startTimes;
	private long systemStartPlayTime;
	private double frameStartPlayTime;
	private boolean playSmoothly = true;
	private int frameNr, prevFrameNr;
	private Timer failDetectTimer;

	/**
	 * Creates a FFMPegVideo and loads a video file specified by name
	 * 
	 * @param fileName
	 *            the name of the video file
	 * @throws IOException
	 */
	public FFMPegVideo(final String fileName) throws IOException {
		Frame[] frames = Frame.getFrames();
		for (int i = 0, n = frames.length; i < n; i++) {
			if (frames[i].getName().equals("Tracker")) { //$NON-NLS-1$
				addPropertyChangeListener(
						"progress", (PropertyChangeListener) frames[i]); //$NON-NLS-1$
				addPropertyChangeListener(
						"stalled", (PropertyChangeListener) frames[i]); //$NON-NLS-1$
				break;
			}
		}
		// timer to detect failures
		failDetectTimer = new Timer(6000, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (frameNr == prevFrameNr) {
					firePropertyChange("stalled", null, fileName); //$NON-NLS-1$
					failDetectTimer.stop();
				}
				prevFrameNr = frameNr;
			}
		});
		failDetectTimer.setRepeats(true);
		load(fileName);
	}

	/**
	 * Plays the video at the current rate. Overrides VideoAdapter method.
	 */
	public void play() {
		if (getFrameCount() == 1) {
			return;
		}
		int n = getFrameNumber() + 1;
		playing = true;
		support.firePropertyChange("playing", null, new Boolean(true)); //$NON-NLS-1$
		startPlayingAtFrame(n);
	}

	/**
	 * Stops the video.
	 */
	public void stop() {
		playing = false;
		support.firePropertyChange("playing", null, new Boolean(false)); //$NON-NLS-1$
	}

	/**
	 * Sets the frame number. Overrides VideoAdapter setFrameNumber method.
	 * 
	 * @param n
	 *            the desired frame number
	 */
	public void setFrameNumber(int n) {
		if (n == getFrameNumber())
			return;
		super.setFrameNumber(n);
		BufferedImage bi = getImage(getFrameNumber());
		if (bi != null) {
			rawImage = bi;
			isValidImage = false;
			isValidFilteredImage = false;
			firePropertyChange(
					"framenumber", null, new Integer(getFrameNumber())); //$NON-NLS-1$
			if (isPlaying()) {
				Runnable runner = new Runnable() {
					public void run() {
						continuePlaying();
					}
				};
				SwingUtilities.invokeLater(runner);
			}
		}
	}

	/**
	 * Gets the start time of the specified frame in milliseconds.
	 * 
	 * @param n
	 *            the frame number
	 * @return the start time of the frame in milliseconds, or -1 if not known
	 */
	public double getFrameTime(int n) {
		if ((n >= startTimes.length) || (n < 0)) {
			return -1;
		}
		return startTimes[n];
	}

	/**
	 * Gets the current frame time in milliseconds.
	 * 
	 * @return the current time in milliseconds, or -1 if not known
	 */
	public double getTime() {
		return getFrameTime(getFrameNumber());
	}

	/**
	 * Sets the frame number to (nearly) a desired time in milliseconds.
	 * 
	 * @param millis
	 *            the desired time in milliseconds
	 */
	public void setTime(double millis) {
		millis = Math.abs(millis);
		for (int i = 0; i < startTimes.length; i++) {
			double t = startTimes[i];
			if (millis < t) { // find first frame with later start time
				setFrameNumber(i - 1);
				break;
			}
		}
	}

	/**
	 * Gets the start frame time in milliseconds.
	 * 
	 * @return the start time in milliseconds, or -1 if not known
	 */
	public double getStartTime() {
		return getFrameTime(getStartFrameNumber());
	}

	/**
	 * Sets the start frame to (nearly) a desired time in milliseconds.
	 * 
	 * @param millis
	 *            the desired start time in milliseconds
	 */
	public void setStartTime(double millis) {
		millis = Math.abs(millis);
		for (int i = 0; i < startTimes.length; i++) {
			double t = startTimes[i];
			if (millis < t) { // find first frame with later start time
				setStartFrameNumber(i - 1);
				break;
			}
		}
	}

	/**
	 * Gets the end frame time in milliseconds.
	 * 
	 * @return the end time in milliseconds, or -1 if not known
	 */
	public double getEndTime() {
		int n = getEndFrameNumber();
		if (n < getFrameCount() - 1)
			return getFrameTime(n + 1);
		return getDuration();
	}

	/**
	 * Sets the end frame to (nearly) a desired time in milliseconds.
	 * 
	 * @param millis
	 *            the desired end time in milliseconds
	 */
	public void setEndTime(double millis) {
		millis = Math.abs(millis);
		millis = Math.min(getDuration(), millis);
		for (int i = 0; i < startTimes.length; i++) {
			double t = startTimes[i];
			if (millis < t) { // find first frame with later start time
				setEndFrameNumber(i - 1);
				break;
			}
		}
	}

	/**
	 * Gets the duration of the video.
	 * 
	 * @return the duration of the video in milliseconds, or -1 if not known
	 */
	public double getDuration() {
		int n = getFrameCount() - 1;
		if (n == 0)
			return 100; // arbitrary duration for single-frame video!
		// assume last and next-to-last frames have same duration
		double delta = getFrameTime(n) - getFrameTime(n - 1);
		return getFrameTime(n) + delta;
	}

	/**
	 * Sets the relative play rate. Overrides VideoAdapter method.
	 * 
	 * @param rate
	 *            the relative play rate.
	 */
	public void setRate(double rate) {
		super.setRate(rate);
		if (isPlaying()) {
			startPlayingAtFrame(getFrameNumber());
		}
	}

	private long getTimeStamp(Pointer<AVFrame> frame) {
		long pts = av_frame_get_best_effort_timestamp(frame);
		if( pts == AV_NOPTS_VALUE)
			pts = 0;
		return pts;
	}
	
	/**
	 * Disposes of this video.
	 */
	public void dispose() {
		super.dispose();
		if(converter != null) {
			converter.dispose();
		}
		if (cContext != null) {
			AvcodecLibrary.avcodec_close(cContext);
			cContext = null;
		}
		if (stream != null) {
			AvcodecLibrary.avcodec_close(stream.get().codec());
			stream = null;
		}
		if (picture != null) {
			if (picture.getValidElements() > 0)
				av_freep(picture);
			picture = null;
		}
		packet = null;
		frame = null;
		if (context != null) {
			AvformatLibrary.avformat_close_input(context.getReference());
			context = null;
		}
	}

	/**
	 * Sets the playSmoothly flag.
	 * 
	 * @param smooth
	 *            true to play smoothly
	 */
	public void setSmoothPlay(boolean smooth) {
		playSmoothly = smooth;
	}

	/**
	 * Gets the playSmoothly flag.
	 * 
	 * @return true if playing smoothly
	 */
	public boolean isSmoothPlay() {
		return playSmoothly;
	}

	// ______________________________ private methods _________________________

	/**
	 * Sets the system and frame start times.
	 * 
	 * @param frameNumber
	 *            the frame number at which playing will start
	 */
	private void startPlayingAtFrame(int frameNumber) {
		// systemStartPlayTime is the system time when play starts
		systemStartPlayTime = System.currentTimeMillis();
		// frameStartPlayTime is the frame time where play starts
		frameStartPlayTime = getFrameTime(frameNumber);
		setFrameNumber(frameNumber);
	}

	/**
	 * Plays the next time-appropriate frame at the current rate.
	 */
	private void continuePlaying() {
		int n = getFrameNumber();
		if (n < getEndFrameNumber()) {
			long elapsedTime = System.currentTimeMillis() - systemStartPlayTime;
			double frameTime = frameStartPlayTime + getRate() * elapsedTime;
			int frameToPlay = getFrameNumberBefore(frameTime);
			while (frameToPlay > -1 && frameToPlay <= n) {
				elapsedTime = System.currentTimeMillis() - systemStartPlayTime;
				frameTime = frameStartPlayTime + getRate() * elapsedTime;
				frameToPlay = getFrameNumberBefore(frameTime);
			}
			if (frameToPlay == -1)
				frameToPlay = getEndFrameNumber();
			// startPlayingAtFrame(frameToPlay);
			setFrameNumber(frameToPlay);
		} else if (looping) {
			startPlayingAtFrame(getStartFrameNumber());
		} else {
			stop();
		}
	}

	/**
	 * Gets the number of the last frame before the specified time.
	 * 
	 * @param time
	 *            the time in milliseconds
	 * @return the frame number, or -1 if not found
	 */
	private int getFrameNumberBefore(double time) {
		for (int i = 0; i < startTimes.length; i++) {
			if (time < startTimes[i])
				return i - 1;
		}
		// if not found, see if specified time falls in last frame
		int n = startTimes.length - 1;
		// assume last and next-to-last frames have same duration
		double endTime = 2 * startTimes[n] - startTimes[n - 1];
		if (time < endTime)
			return n;
		return -1;
	}

	/**
	 * Loads a video specified by name.
	 * 
	 * @param fileName
	 *            the video file name
	 * @throws IOException
	 */
	private void load(String fileName) throws IOException {
		Resource res = ResourceLoader.getResource(fileName);
		if (res == null) {
			throw new IOException("unable to create resource for " + fileName); //$NON-NLS-1$
		}
		// create and open a FFMPeg container
		URL url = res.getURL();
		boolean isLocal = url.getProtocol().toLowerCase().indexOf("file") > -1; //$NON-NLS-1$
		String path = isLocal ? res.getAbsolutePath() : url.toExternalForm();
		OSPLog.finest("FFMPeg video loading " + path + " local?: " + isLocal); //$NON-NLS-1$ //$NON-NLS-2$
		Pointer<Pointer<AVFormatContext>> pfmt_ctx = Pointer
				.allocatePointer(AVFormatContext.class);
		if (avformat_open_input(pfmt_ctx, Pointer.pointerToCString(path), null,
				null) < 0) {
			dispose();
			throw new IOException("unable to open " + fileName); //$NON-NLS-1$
		}
		context = pfmt_ctx.get();
		/* retrieve stream information */
		if (avformat_find_stream_info(context, null) < 0) {
			dispose();
			throw new IOException("unable to find stream info in " + fileName); //$NON-NLS-1$
		}

		// set up frame data using FFMPegAnalyzer object
		FFMPegAnalyzer analyzer = null;
		failDetectTimer.start();
		frameNr = prevFrameNr = 0;
		try {
			analyzer = new FFMPegAnalyzer(path, support);
			streamIndex = analyzer.getVideoStreamIndex();
			frameTimeStamps = analyzer.getFrameTimeStamps();
			keyTimeStamps = analyzer.getKeyTimeStamps();

			// set initial video clip properties
			frameCount = frameTimeStamps.size();
			startFrameNumber = 0;
			endFrameNumber = frameCount - 1;
			
			// create startTimes array
			startTimes = analyzer.getStartTimes();
		} catch(IOException e) {
			failDetectTimer.stop();
			dispose();
			throw new IOException(e.getLocalizedMessage());
		}
		
		stream = context.get().streams().get(streamIndex);
		/* find decoder for the stream */
		cContext = stream.get().codec();
		codec = avcodec_find_decoder(cContext.get().codec_id());
		if (codec == null) {
			dispose();
			throw new IOException(
					"unable to find codec video stream in " + fileName); //$NON-NLS-1$			
		}
		
		// check that coder opens
		if (avcodec_open2(cContext, codec, null) < 0) {
			dispose();
			throw new IOException(
					"unable to open video decoder for " + fileName); //$NON-NLS-1$
		}
		timebase = copy(stream.get().time_base());

		// throw IOException if no frames were loaded
		if (frameTimeStamps.size() == 0) {
			firePropertyChange("progress", fileName, null); //$NON-NLS-1$
			failDetectTimer.stop();
			dispose();
			// VideoIO.setCanceled(true);
			throw new IOException("packets loaded but no complete picture"); //$NON-NLS-1$
		}

		// set properties
		setProperty("name", XML.getName(fileName)); //$NON-NLS-1$
		setProperty("absolutePath", res.getAbsolutePath()); //$NON-NLS-1$
		if (fileName.indexOf(":") == -1) { //$NON-NLS-1$
			// if name is relative, path is name
			setProperty("path", XML.forwardSlash(fileName)); //$NON-NLS-1$
		} else {
			// else path is relative to user directory
			setProperty("path", XML.getRelativePath(fileName)); //$NON-NLS-1$
		}

		// initialize frame, packet, picture and image
		/* allocate image where the decoded image will be put */
		picture_bufsize = AvutilLibrary.av_image_alloc(picture,
				picture_linesize, cContext.get().width(), cContext.get()
						.height(), cContext.get().pix_fmt(), 1);
		if (picture_bufsize < 0) {
			dispose();
			throw new IOException(
					"unable to allocate raw memory buffer for " + fileName); //$NON-NLS-1$
		}
		packet = Pointer.allocate(AVPacket.class);
		av_init_packet(packet);
		packet.get().data(null);
		packet.get().size(0);
		frame = Pointer.allocate(AVFrame.class);
		loadNextFrame();
		BufferedImage img = getImage(0);
		if (img == null) {
			for (int i = 1; i < frameTimeStamps.size(); i++) {
				img = getImage(i);
				if (img != null)
					break;
			}
		}
		firePropertyChange("progress", fileName, null); //$NON-NLS-1$
		failDetectTimer.stop();
		if (img == null) {
			dispose();
			throw new IOException("No images"); //$NON-NLS-1$
		}
		setImage(img);
	}

	/**
	 * Reloads the current video.
	 * 
	 * @throws IOException
	 */
	private void reload() throws IOException {
		String url = context.get().filename().getCString();
		if (context != null) {
			AvformatLibrary.avformat_close_input(context.getReference());
			context = null;
		}
		if (cContext != null) {
			AvcodecLibrary.avcodec_close(cContext);
			cContext = null;
		}
		stream = null;
		boolean isLocal = url.toLowerCase().indexOf("file:") > -1; //$NON-NLS-1$
		String path = isLocal ? ResourceLoader.getNonURIPath(url) : url;
		Pointer<Pointer<AVFormatContext>> pfmt_ctx = Pointer
				.allocatePointer(AVFormatContext.class);
		if (AvformatLibrary.avformat_open_input(pfmt_ctx,
				Pointer.pointerToCString(path), null, null) < 0) {
			dispose();
			throw new IOException("unable to open " + path); //$NON-NLS-1$
		}
		context = pfmt_ctx.get();
		stream = context.get().streams().get(streamIndex);
		cContext = stream.get().codec();
		codec = avcodec_find_decoder(cContext.get().codec_id());
		if (codec == null) {
			dispose();
			throw new IOException(
					"unable to find codec video stream in " + path); //$NON-NLS-1$			
		}

		// check that coder opens
		if (avcodec_open2(cContext, codec, null) < 0) {
			dispose();
			throw new IOException("unable to open video decoder for " + path); //$NON-NLS-1$
		}
		timebase = copy(stream.get().time_base());
	}

	/**
	 * Sets the initial image.
	 * 
	 * @param image
	 *            the image
	 */
	private void setImage(BufferedImage image) {
		rawImage = image;
		size = new Dimension(image.getWidth(), image.getHeight());
		refreshBufferedImage();
		// create coordinate system and relativeAspects
		coords = new ImageCoordSystem(frameCount);
		coords.addPropertyChangeListener(this);
		aspects = new DoubleArray(frameCount, 1);
	}

	/**
	 * Returns the key frame with the specified timestamp.
	 * 
	 * @param timestamp
	 *            the timestamp in stream timebase units
	 * @return true if frame found and loaded
	 */
	private boolean loadKeyFrame(long timestamp) {
		// compare requested timestamp with current frame
		long delta = timestamp - getTimeStamp(frame);
		long currenttimestamp = Integer.MIN_VALUE;
		// if delta is zero, return frame
		if (delta == 0) {
			return true;
		}
		// if delta is positive and short, step forward
		AVRational timebase = null;
		timebase = copy(stream.get().time_base());
		int shortTime = timebase != null ? (int)(1.0/av_q2d(timebase)) : 1; // one second
		if (delta > 0 && delta < shortTime) {
			while (loadNextFrame()) {
				currenttimestamp = getTimeStamp(frame);
				if (isKeyFrame(frame) && currenttimestamp == timestamp) {
					return true;
				}
				if (currenttimestamp > timestamp) {
					delta = timestamp - currenttimestamp;
					break;
				}
			}
		}
		// if delta is positive and long, seek forward
		if (delta > 0
				&& AvformatLibrary.av_seek_frame(context, streamIndex,
						timestamp, 0) >= 0) {
			avcodec_flush_buffers(cContext);
			while (loadNextFrame()) {
				currenttimestamp = getTimeStamp(frame);
				if (isKeyFrame(frame) && currenttimestamp == timestamp) {
					return true;
				}
				if (currenttimestamp > timestamp) {
					delta = timestamp - currenttimestamp;
					break;
				}
			}
		}
		// if delta is negative, seek backward
		if (getFrameNumber(timestamp) == 0) {
			resetContainer();
			return true;
		}
		if (delta < 0
				&& AvformatLibrary.av_seek_frame(context, streamIndex,
						timestamp, AvformatLibrary.AVSEEK_FLAG_BACKWARD) >= 0) {
			avcodec_flush_buffers(cContext);
			while (loadNextFrame()) {
				currenttimestamp = getTimeStamp(frame);
				if (isKeyFrame(frame) && currenttimestamp == timestamp) {
					return true;
				}
				if (currenttimestamp > timestamp) {
					delta = timestamp - currenttimestamp;
					break;
				}
			}
		}

		// if all else fails, reopen container and step forward
		resetContainer();
		while (loadNextFrame()) {
			currenttimestamp = getTimeStamp(frame);
			if (isKeyFrame(frame) && currenttimestamp == timestamp) {
				return true;
			}
			if (currenttimestamp > timestamp) {
				break;
			}
		}

		// if still not found, return false
		return false;
	}

	/**
	 * Gets the key frame needed to display a specified frame.
	 * 
	 * @param frameNumber
	 *            the frame number
	 * @return true, if frame found
	 */
	private boolean loadKeyFrameForFrame(int frameNumber) {
		long keyTimeStamp = keyTimeStamps.get(frameNumber);
		return loadKeyFrame(keyTimeStamp);
	}

	/**
	 * Loads the FFMPeg picture with all data needed to display a specified
	 * frame.
	 * 
	 * @param frameNumber
	 *            the frame number to load
	 * @return true if loaded successfully
	 */
	private boolean loadPicture(int frameNumber) {
		// check to see if seek is needed
		long currentTS = getTimeStamp(frame);
		long targetTS = getTimeStamp(frameNumber);
		long keyTS = keyTimeStamps.get(frameNumber);
		if (currentTS == targetTS) {
			// frame is already loaded
			return true;
		}
		if (currentTS >= keyTS && currentTS < targetTS) {
			// no need to seek--just step forward
			if (loadNextFrame()) {
				int n = getFrameNumber(frame);
				while (n > -2 && n < frameNumber) {
					if (loadNextFrame()) {
						n = getFrameNumber(frame);
					} else
						return false;
				}
			} else
				return false;
		} else if (loadKeyFrameForFrame(frameNumber)) {
			int n = getFrameNumber(frame);
			while (n > -2 && n < frameNumber) {
				if (loadNextFrame()) {
					n = getFrameNumber(frame);
				} else
					return false;
			}
		}
		return true;
	}

	/**
	 * Gets the timestamp for a specified frame.
	 * 
	 * @param frameNumber
	 *            the frame number
	 * @return the timestamp in stream timebase units
	 */
	private long getTimeStamp(int frameNumber) {
		return frameTimeStamps.get(frameNumber);
	}

	/**
	 * Gets the frame number for a specified timestamp.
	 * 
	 * @param timeStamp
	 *            the timestamp in stream timebase units
	 * @return the frame number, or -1 if not found
	 */
	private int getFrameNumber(long timeStamp) {
		for (int i = 0; i < frameTimeStamps.size(); i++) {
			long ts = frameTimeStamps.get(i);
			if (ts == timeStamp)
				return i;
		}
		return -1;
	}

	/**
	 * Gets the frame number for a specified frame.
	 * 
	 * @param packet
	 *            the packet
	 * @return the frame number, or -2 if null
	 */
	private int getFrameNumber(Pointer<AVFrame> frame) {
		if (frame == null)
			return -2;
		return getFrameNumber(getTimeStamp(frame));
	}

	/**
	 * Gets the BufferedImage for a specified frame.
	 * 
	 * @param frameNumber
	 *            the frame number
	 * @return the image, or null if failed to load
	 */
	private BufferedImage getImage(int frameNumber) {
		if (frameNumber < 0 || frameNumber >= frameTimeStamps.size()) {
			return null;
		}
		if (loadPicture(frameNumber)) {
			// convert picture to buffered image and display
			return getBufferedImageFromPicture();
		}
		return null;
	}

	/**
	 * Gets the BufferedImage for a specified ffmpeg picture.
	 * 
	 * @param picture
	 *            the picture
	 * @return the image, or null if unable to resample
	 */
	private BufferedImage getBufferedImageFromPicture() {
		// use BgrConverter to convert picture to buffered image
		try {
		if (converter == null) {
			converter = new BgrConverter(cContext.get().pix_fmt(), cContext.get().width(), cContext.get()
					.height());
		}
		} catch(IOException e) {
			return null;
		}
		
		BufferedImage image = null;
		image = converter.toImage(picture, picture_linesize, picture_bufsize);
		// garbage collect to play smoothly--but slows down playback speed
		// significantly!
		if (playSmoothly)
			System.gc();
		return image;
	}

	/**
	 * Loads the next video frame in the container into the current FFMPeg
	 * picture.
	 * 
	 * @return true if successfully loaded
	 */
	private boolean loadNextFrame() {
		while (av_read_frame(context, packet) >= 0) {
			Pointer<AVPacket> origPacket = packet;
			try {
				if (isVideoPacket(packet, streamIndex)) {
					// long timeStamp = packet.getTimeStamp();
					// System.out.println("loading next packet at "+timeStamp+": "+packet.getSize());
					if( loadFrame() )
						return true;
				}
			} finally {
				if (origPacket != null) {
					av_free_packet(origPacket);
				}
			}
		}
		/* load cached frame */
		packet.get().data(null);
		packet.get().size(0);
		if ( loadFrame() )
			return true;
		return false;
	}

	/**
	 * Loads a video frame into the current ffmpeg picture.
	 * 
	 * @return true if successfully loaded, false if no more 
	 *              frames or no frame loaded.
	 */
	private boolean loadFrame() {
		if (frame == null || packet == null)
			return false;
		int bytesDecoded;
		long ptr = 0;
		do {
			if(packet.get().size() > 0)
				ptr = packet.get().data().getPeer();
			Pointer<Integer> got_frame = Pointer.allocateInt();
			// decode the frame into the picture
			bytesDecoded = avcodec_decode_video2(
					cContext, frame, got_frame, packet);
			// check for errors
			if (bytesDecoded < 0)
				return false;
	
			if (got_frame.getInt() == 1) {
				copyToPicture(frame);
				return true;
			}
			if(packet.get().size() > 0) {
				ptr+=bytesDecoded;
				packet.get().data((Pointer<Byte>)Pointer.pointerToAddress(ptr));
				packet.get().size(packet.get().size()-bytesDecoded);
			}
		} while (packet.get().size() > 0);
		return false;
	}

	private void copyToPicture(Pointer<AVFrame> frame) {
		/*
		 * copy decoded frame to destination buffer: this is required since
		 * rawvideo expects non aligned data
		 */
		av_image_copy(picture, picture_linesize, frame.get().data(), frame
				.get().linesize(), cContext.get().pix_fmt(), cContext.get()
				.width(), cContext.get().height());
	}

	/**
	 * Resets the container to the beginning.
	 */
	private void resetContainer() {
		// seek backwards--this will fail for streamed web videos
		if (AvformatLibrary.av_seek_frame(context, streamIndex, getTimeStamp(0), 
				AvformatLibrary.AVSEEK_FLAG_BACKWARD) >= 0) {
			avcodec_flush_buffers(cContext);
			loadNextFrame();
		} else {
			try {
				reload();
				loadNextFrame();
			} catch (IOException e) {
				OSPLog.warning("Container could not be reset"); //$NON-NLS-1$   	
			}
		}
	}

	/**
	 * Returns an XML.ObjectLoader to save and load FFMPegVideo data.
	 * 
	 * @return the object loader
	 */
	public static XML.ObjectLoader getLoader() {
		return new Loader();
	}

	/**
	 * A class to save and load FFMPegVideo data.
	 */
	static class Loader implements XML.ObjectLoader {
		/**
		 * Saves FFMPegVideo data to an XMLControl.
		 * 
		 * @param control
		 *            the control to save to
		 * @param obj
		 *            the FFMPegVideo object to save
		 */
		public void saveObject(XMLControl control, Object obj) {
			FFMPegVideo video = (FFMPegVideo) obj;
			String base = (String) video.getProperty("base"); //$NON-NLS-1$
			String absPath = (String) video.getProperty("absolutePath"); //$NON-NLS-1$
			control.setValue("path", XML.getPathRelativeTo(absPath, base)); //$NON-NLS-1$
			if (!video.getFilterStack().isEmpty()) {
				control.setValue("filters", video.getFilterStack().getFilters()); //$NON-NLS-1$
			}
		}

		/**
		 * Creates a new FFMPegVideo.
		 * 
		 * @param control
		 *            the control
		 * @return the new FFMPegVideo
		 */
		public Object createObject(XMLControl control) {
			try {
				String path = control.getString("path"); //$NON-NLS-1$
				String ext = XML.getExtension(path);
				FFMPegVideo video = new FFMPegVideo(path);
				VideoType ffmpegType = VideoIO.getVideoType(
						VideoIO.ENGINE_FFMPEG, ext);
				if (ffmpegType != null)
					video.setProperty("video_type", ffmpegType); //$NON-NLS-1$
				return video;
			} catch (IOException ex) {
				OSPLog.fine(ex.getMessage());
				return null;
			}
		}

		/**
		 * Loads properties into a FFMPegVideo.
		 * 
		 * @param control
		 *            the control
		 * @param obj
		 *            the FFMPegVideo object
		 * @return the loaded object
		 */
		public Object loadObject(XMLControl control, Object obj) {
			FFMPegVideo video = (FFMPegVideo) obj;
			Collection<?> filters = (Collection<?>) control
					.getObject("filters"); //$NON-NLS-1$
			if (filters != null) {
				video.getFilterStack().clear();
				Iterator<?> it = filters.iterator();
				while (it.hasNext()) {
					Filter filter = (Filter) it.next();
					video.getFilterStack().addFilter(filter);
				}
			}
			return obj;
		}
	}

}
