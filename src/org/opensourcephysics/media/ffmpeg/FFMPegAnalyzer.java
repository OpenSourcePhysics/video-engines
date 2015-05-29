package org.opensourcephysics.media.ffmpeg;

import static org.ffmpeg.avcodec.AvcodecLibrary.AV_PKT_FLAG_KEY;
import static org.ffmpeg.avcodec.AvcodecLibrary.av_free_packet;
import static org.ffmpeg.avcodec.AvcodecLibrary.av_init_packet;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_decode_video2;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_find_decoder;
import static org.ffmpeg.avcodec.AvcodecLibrary.avcodec_open2;
import static org.ffmpeg.avformat.AvformatLibrary.av_find_best_stream;
import static org.ffmpeg.avformat.AvformatLibrary.av_read_frame;
import static org.ffmpeg.avformat.AvformatLibrary.av_register_all;
import static org.ffmpeg.avformat.AvformatLibrary.avformat_close_input;
import static org.ffmpeg.avformat.AvformatLibrary.avformat_find_stream_info;
import static org.ffmpeg.avformat.AvformatLibrary.avformat_open_input;
import static org.ffmpeg.avutil.AvutilLibrary.AV_NOPTS_VALUE;
import static org.ffmpeg.avutil.AvutilLibrary.av_frame_get_best_effort_timestamp;
import static org.ffmpeg.avutil.AvutilLibrary.av_image_copy;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bridj.Pointer;
import org.ffmpeg.avcodec.AVCodec;
import org.ffmpeg.avcodec.AVCodecContext;
import org.ffmpeg.avcodec.AVPacket;
import org.ffmpeg.avcodec.AvcodecLibrary;
import org.ffmpeg.avformat.AVFormatContext;
import org.ffmpeg.avformat.AVStream;
import org.ffmpeg.avutil.AVFrame;
import org.ffmpeg.avutil.AVRational;
import org.ffmpeg.avutil.AvutilLibrary;
import org.ffmpeg.avutil.AvutilLibrary.AVMediaType;
import org.opensourcephysics.media.core.VideoIO;

public class FFMPegAnalyzer {

	// path to the video file
	String path;
	// PropertyChangeSupport object to notify
	PropertyChangeSupport support;
	// timebase
	AVRational timebase;

	// video stream index or -1 if none
	private int streamIndex;
	// maps frame number to timestamp of displayed packet (last packet loaded)
	private Map<Integer, Long> frameTimeStamps;
	// maps frame number to timestamp of key packet (first packet loaded)
	private Map<Integer, Long> keyTimeStamps;
	// seconds array to calculate startTimes
	private ArrayList<Double> seconds;

	// create thumbnail image
	private Pointer<Pointer<Byte>> picture;
	private Pointer<Integer> picture_linesize;
	int picture_bufsize;
	BgrConverter converter;
	private BufferedImage thumbnail;

	private boolean createThumbnail;
	private int targetFrameNumber;
	private boolean analyzed;

	public FFMPegAnalyzer(String path, PropertyChangeSupport support) {
		frameTimeStamps = new HashMap<Integer, Long>();
		keyTimeStamps = new HashMap<Integer, Long>();
		createThumbnail = false;
		targetFrameNumber = 15;
		analyzed = false;
		thumbnail = null;
		this.path = path;
		this.streamIndex = -1;
		timebase = null;
		this.support = support;
		seconds = new ArrayList<Double>();
		picture = null;
		picture_linesize = null;
		picture_bufsize = 0;
		converter = null;
	}

	public FFMPegAnalyzer(String path, boolean createThumbnail,
			int targetFrameNumber) {
		this(path, null);
		this.createThumbnail = createThumbnail;
		this.targetFrameNumber = targetFrameNumber;
		if (createThumbnail) {
			picture = Pointer.allocatePointers(Byte.class, 4);
			picture_linesize = Pointer.allocateInts(4);
		}
	}

	public int getVideoStreamIndex() throws IOException {
		if (!analyzed)
			analyze();
		return streamIndex;
	}

	public Map<Integer, Long> getFrameTimeStamps() throws IOException {
		if (!analyzed)
			analyze();
		return frameTimeStamps;
	}

	public Map<Integer, Long> getKeyTimeStamps() throws IOException {
		if (!analyzed)
			analyze();
		return keyTimeStamps;
	}

	public double[] getStartTimes() throws IOException {
		if (!analyzed)
			analyze();
		double[] startTimes = new double[frameTimeStamps.size()];
		if(startTimes.length < 1)
			return startTimes;
		startTimes[0] = 0;
		for (int i = 1; i < startTimes.length; i++) {
			startTimes[i] = seconds.get(i) * 1000;
		}
		return startTimes;
	}

	public BufferedImage getThumbnail() throws IOException {
		if (!analyzed)
			analyze();
		return thumbnail;
	}

	private long getTimeStamp(Pointer<AVFrame> pFrame) {
		long pts = av_frame_get_best_effort_timestamp(pFrame);
		if( pts == AV_NOPTS_VALUE)
			pts = 0;
		return pts;
	}
	
	public void analyze() throws IOException {
		Pointer<AVFormatContext> context = null;
		Pointer<AVStream> stream = null;
		Pointer<AVCodecContext> cContext = null;
		Pointer<AVCodec> codec = null;
		Pointer<AVPacket> packet = null, orig_packet = null;
		Pointer<AVFrame> frame = null;
		Pointer<Integer> got_frame = null;
		try {
			av_register_all();
			// set up frame data using temporary container
			Pointer<Pointer<AVFormatContext>> pfmt_ctx = Pointer
					.allocatePointer(AVFormatContext.class);
			if (avformat_open_input(pfmt_ctx, Pointer.pointerToCString(path),
					null, null) < 0) {
				throw new IOException("unable to open " + path); //$NON-NLS-1$
			}
			context = pfmt_ctx.get();
			/* retrieve stream information */
			if (avformat_find_stream_info(context, null) < 0) {
				throw new IOException("unable to find stream info in " + path); //$NON-NLS-1$
			}

			// find the first video stream in the container
			int ret = av_find_best_stream(context,
					AVMediaType.AVMEDIA_TYPE_VIDEO, -1, -1, null, 0);
			streamIndex = -1;
			if (ret < 0) {
				throw new IOException("unable to find video stream in " + path); //$NON-NLS-1$			
			}
			streamIndex = ret;
			if (streamIndex < 0) {
				throw new IOException("unable to find video stream in " + path); //$NON-NLS-1$			
			}
			stream = context.get().streams().get(streamIndex);
			timebase = copy(stream.get().time_base());
			
			cContext = stream.get().codec();
			codec = avcodec_find_decoder(cContext.get().codec_id());
			if (codec == null) {
				throw new IOException(
						"unable to find codec video stream in " + path); //$NON-NLS-1$			
			}
			// check that coder opens
			if (avcodec_open2(cContext, codec, null) < 0) {
				throw new IOException(
						"unable to open video decoder for " + path); //$NON-NLS-1$
			}

			packet = Pointer.allocate(AVPacket.class);
			av_init_packet(packet);
			packet.get().data(null);
			packet.get().size(0);

			long keyTimeStamp = Long.MIN_VALUE;
			long startTimeStamp = Long.MIN_VALUE;
			long pts;
			seconds = new ArrayList<Double>();
			if (support != null)
				support.firePropertyChange("progress", path, 0); //$NON-NLS-1$
			int frameNr = 0;

			// step thru container and find all video frames
			/* allocate image where the decoded image will be put */
			if (createThumbnail) {
				picture_bufsize = AvutilLibrary.av_image_alloc(picture,
						picture_linesize, cContext.get().width(), cContext
								.get().height(), cContext.get().pix_fmt(), 1);
				if (picture_bufsize < 0) {
					throw new IOException(
							"unable to allocate raw memory buffer for " + path); //$NON-NLS-1$
				}
				converter = new BgrConverter(cContext.get().pix_fmt(), cContext
						.get().width(), cContext.get().height());
			}
			frame = Pointer.allocate(AVFrame.class);
			got_frame = Pointer.allocateInt();
			while (av_read_frame(context, packet) >= 0) {
				if (VideoIO.isCanceled()) {
					if (support != null)
						support.firePropertyChange("progress", path, null); //$NON-NLS-1$
					throw new IOException("Canceled by user"); //$NON-NLS-1$
				}
				if (isVideoPacket(packet, streamIndex)) {
					orig_packet = packet;
					long ptr = packet.get().data().getPeer();
					int bytesDecoded;
					do {
						/* decode video frame */
						bytesDecoded = avcodec_decode_video2(cContext, frame,
								got_frame, packet);
						// check for errors
						if (bytesDecoded < 0)
							break;
						if (got_frame.get() != 0) {
							pts = getTimeStamp(frame);
							if (keyTimeStamp == Long.MIN_VALUE
									|| isKeyFrame(frame)) {
								keyTimeStamp = pts;
							}
							if (startTimeStamp == Long.MIN_VALUE) {
								startTimeStamp = pts;
							}
							frameTimeStamps.put(frameNr, pts);
							seconds.add((double) ((pts - startTimeStamp) * value(timebase)));
							keyTimeStamps.put(frameNr, keyTimeStamp);
							if (support != null)
								support.firePropertyChange(
										"progress", path, frameNr); //$NON-NLS-1$
							if (createThumbnail) {
								/*
								 * copy decoded frame to destination buffer: this is
								 * required since rawvideo expects non aligned data
								 */
								av_image_copy(picture, picture_linesize, frame
										.get().data(), frame.get().linesize(),
										cContext.get().pix_fmt(), cContext.get()
												.width(), cContext.get().height());
								thumbnail = converter.toImage(picture, picture_linesize, picture_bufsize);
							}
							frameNr++;
						}
						ptr+=bytesDecoded;
						packet.get().data((Pointer<Byte>)Pointer.pointerToAddress(ptr));
						packet.get().size(packet.get().size()-bytesDecoded);
					} while(packet.get().size() > 0);
				}
				av_free_packet(packet);
				if (createThumbnail
						&& (thumbnail != null || frameNr >= targetFrameNumber))
					break;
			}
			/* flush cached frames */
			packet.get().data(null);
			packet.get().size(0);

			do {
				if (createThumbnail
						&& (thumbnail != null || frameNr >= targetFrameNumber))
					break;
				/* decode video frame */
				avcodec_decode_video2(cContext, frame, got_frame, packet);
				if (got_frame.get() != 0) {
					pts = getTimeStamp(frame);
					if (keyTimeStamp == Long.MIN_VALUE || isKeyFrame(frame)) {
						keyTimeStamp = pts;
					}
					if (startTimeStamp == Long.MIN_VALUE) {
						startTimeStamp = pts;
					}
					frameTimeStamps.put(frameNr, pts);
					seconds.add((double) ((pts - startTimeStamp) * value(timebase)));
					keyTimeStamps.put(frameNr, keyTimeStamp);
					if (support != null)
						support.firePropertyChange("progress", path, frameNr); //$NON-NLS-1$
					frameNr++;
				}
			} while (got_frame.get() != 0);
		} finally {
			// clean up temporary objects
			AvcodecLibrary.avcodec_close(cContext);
			cContext = null;
			stream = null;
			packet = null;
			frame = null;
			if (context != null) {
				avformat_close_input(context.getReference());
				context = null;
			}
			if (converter != null) {
				converter.dispose();
				converter = null;
			}
		}
		analyzed = true;
	}

	/**
	 * Determines if a frame is a key frame.
	 * 
	 * @param packet
	 *            the frame
	 * @return true if frame is a key in the video stream
	 */
	public static boolean isKeyFrame(Pointer<AVFrame> frame) {
		if ((frame.get().flags() & AV_PKT_FLAG_KEY) != 0) {
			return true;
		}
		return false;
	}

	/**
	 * Determines if a packet is a video packet.
	 * 
	 * @param packet
	 *            the packet
	 * @return true if packet is in the video stream
	 */
	public static boolean isVideoPacket(Pointer<AVPacket> packet,
			int videoStreamIndex) {
		if (packet.get().stream_index() == videoStreamIndex) {
			return true;
		}
		return false;
	}

	public static AVRational copy(AVRational rat) {
		AVRational ret = new AVRational();
		ret.den(rat.den());
		ret.num(rat.num());
		return ret;
	}

	public static double value(AVRational rat) {
		double ret = 1.0 * rat.num() / rat.den();
		return ret;
	}

}
