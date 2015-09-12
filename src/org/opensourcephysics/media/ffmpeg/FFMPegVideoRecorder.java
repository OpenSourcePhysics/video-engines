package org.opensourcephysics.media.ffmpeg;

import java.awt.Image;
import java.io.IOException;

import org.opensourcephysics.media.core.ScratchVideoRecorder;
import org.opensourcephysics.media.ffmpeg.FFMPegVideoType;

public class FFMPegVideoRecorder extends ScratchVideoRecorder {

	/**
	 * Constructs a FFMPegVideoRecorder object.
	 * 
	 * @param type
	 *            the video type
	 */
	public FFMPegVideoRecorder(FFMPegVideoType type) {
		super(type);
	}

	@Override
	protected void saveScratch() throws IOException {
		// TODO Automatisch generierter Methodenstub

	}

	@Override
	protected boolean startRecording() {
		// TODO Automatisch generierter Methodenstub
		return false;
	}

	@Override
	protected boolean append(Image image) {
		// TODO Automatisch generierter Methodenstub
		return false;
	}

}
