package de.hsrm.blaubot.android.datasourceplugin.sensor;

import java.nio.ByteBuffer;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import de.hsrm.blaubot.datasource.IDataSourcePlugin;
import de.hsrm.blaubot.protocol.StreamManager;
import de.hsrm.blaubot.protocol.client.channel.Channel;

public class RotationSensorPlugin implements IDataSourcePlugin, SensorEventListener {

	private static final String tag = "RotationSensorPlugin";
	private SensorManager mSensorManager;
	private Sensor mRotationVectorSensor;
	private Channel mChannel;

	public RotationSensorPlugin(Channel channel, SensorManager sensorManager) {
		this.mSensorManager = sensorManager;
		this.mChannel = channel;
		mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
	}

	/**
	 * activates the plugin. if there is no RotationVectorSensor available, a NoRotationVectorSensorException will be thrown
	 */
	@Override
	public void activate() throws NoRotationVectorSensorException {
		// some devices don't have a RotationVectorSensor: throw exception
		if (mRotationVectorSensor == null) {
			throw new NoRotationVectorSensorException();
		}
		// we want events as fast as possible
		mSensorManager.registerListener(this, mRotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
		Log.d(tag, "Activated Plugin");
	}

	@Override
	public void deactivate() {
		// make sure to turn our sensor off when the activity is paused
		mSensorManager.unregisterListener(this);
		Log.d(tag, "Deactivated Plugin");
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// we received a sensor event. it is a good practice to check
		// that we received the proper event
		if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			float[] values = event.values;
			// there can be 3, 4 or 5 values (see sensor docs), we send them all
			final int capacity = values.length * Float.SIZE / 8;
			ByteBuffer bb = ByteBuffer.allocate(capacity).order(StreamManager.MY_BYTE_ORDER);
			for (float value : values) {
				bb.putFloat(value);
			}
			byte[] bytes = new byte[capacity];
			bb.clear();
			bb.get(bytes);
			mChannel.post(bytes);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

}
