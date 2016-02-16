package eu.hgross.blaubot.datasource;

/**
 * implement this interface if you want to create a data source plugin for
 * blaubot. once a data source plugin has been activated, it provides data from
 * a specific source such as a rotation sensor or the position of fingers on the
 * touchscreen and sends these data via a specific channel to other devices.
 * @deprecated
 */
public interface IDataSourcePlugin {

	/**
	 * activate this plugin in order to start consuming and sending the data
	 * from the corresponding data source
	 * 
	 * @throws Exception
	 *             if anything goes wrong during activation (such as no rotation
	 *             sensor available when activating a rotation sensor plugin).
	 */
	public void activate() throws Exception;

	/**
	 * deactivate this plugin in order to release resources and stop retrieving
	 * data from the data source.
	 */
	public void deactivate();

}
