package org.opensourcephysics.media.ffmpeg;

import static org.ffmpeg.avformat.AvformatLibrary.av_register_all;

import org.opensourcephysics.controls.OSPLog;
import org.opensourcephysics.media.core.VideoFileFilter;
import org.opensourcephysics.media.core.VideoIO;
import org.opensourcephysics.media.ffmpeg.FFMPegVideoType;
import org.opensourcephysics.tools.ResourceLoader;

/**
 * This registers FFMPeg with VideoIO so it can be used to open and record
 * videos.
 * 
 * @author Frank Schütte
 * @version 1.0
 */
public class FFMPegIO extends VideoIO {
	/**
	 * Registers FFMPeg video types with VideoIO class.
	 */
	static public void registerWithVideoIO() { // add FFMPeg video types, if
												// available
		try {
			VideoIO.addVideoEngine(new FFMPegVideoType());
			// register all supported audio/video types with ffmpeg
			av_register_all();
			// add common video types shared with QuickTime
			for (String ext : VideoIO.VIDEO_EXTENSIONS) { // {"mov", "avi",
															// "mp4"}
				VideoFileFilter filter = new VideoFileFilter(ext,
						new String[] { ext });
				FFMPegVideoType ffmpegType = new FFMPegVideoType(filter);
				VideoIO.addVideoType(ffmpegType);
				ResourceLoader.addExtractExtension(ext);
			}
			// add additional ffmpeg types
			// FLV
			VideoFileFilter filter = new VideoFileFilter(
					"flv", new String[] { "flv" }); //$NON-NLS-1$ //$NON-NLS-2$
			VideoIO.addVideoType(new FFMPegVideoType(filter));
			ResourceLoader.addExtractExtension("flv"); //$NON-NLS-1$
			// 3GP
			filter = new VideoFileFilter("3gp", new String[] { "3gp" }); //$NON-NLS-1$ //$NON-NLS-2$
			FFMPegVideoType vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("3gp"); //$NON-NLS-1$
			// WMV
			filter = new VideoFileFilter("asf", new String[] { "wmv" }); //$NON-NLS-1$ //$NON-NLS-2$
			VideoIO.addVideoType(new FFMPegVideoType(filter));
			ResourceLoader.addExtractExtension("wmv"); //$NON-NLS-1$
			// DV
			filter = new VideoFileFilter("dv", new String[] { "dv" }); //$NON-NLS-1$ //$NON-NLS-2$
			vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("dv"); //$NON-NLS-1$
			// MTS
			filter = new VideoFileFilter("mts", new String[] { "mts" }); //$NON-NLS-1$ //$NON-NLS-2$
			vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("mts"); //$NON-NLS-1$
			// M2TS
			filter = new VideoFileFilter("m2ts", new String[] { "m2ts" }); //$NON-NLS-1$ //$NON-NLS-2$
			vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("m2ts"); //$NON-NLS-1$
			// MPG
			filter = new VideoFileFilter("mpg", new String[] { "mpg" }); //$NON-NLS-1$ //$NON-NLS-2$
			vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("mpg"); //$NON-NLS-1$
			// MOD
			filter = new VideoFileFilter("mod", new String[] { "mod" }); //$NON-NLS-1$ //$NON-NLS-2$
			vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("mod"); //$NON-NLS-1$
			// OGG
			filter = new VideoFileFilter("ogg", new String[] { "ogg", "ogv" }); //$NON-NLS-1$ //$NON-NLS-2$
			vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("mod"); //$NON-NLS-1$
			filter = new VideoFileFilter("webm", new String[] {"webm"}); //$NON-NLS-1$ //$NON-NLS-2$
			vidType = new FFMPegVideoType(filter);
			vidType.setRecordable(false);
			VideoIO.addVideoType(vidType);
			ResourceLoader.addExtractExtension("webm"); //$NON-NLS-1$
		} catch (Exception ex) { // ffmpeg not working
			OSPLog.config("ffmpeg exception: " + ex.toString()); //$NON-NLS-1$
		} catch (Error er) { // ffmpeg not working
			OSPLog.config("ffmpeg error: " + er.toString()); //$NON-NLS-1$
		}
	}

}
