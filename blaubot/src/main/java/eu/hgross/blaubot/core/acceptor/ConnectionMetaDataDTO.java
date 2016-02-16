package eu.hgross.blaubot.core.acceptor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic meta data DTO object to transfer connectivity informations about
 * Acceptors (like ip address, mac address, ...)
 *
 * Each subclass has to set their own connection type value in the meta data map.
 *    (setAcceptorType("YourAcceptor_1.0")
 */
public class ConnectionMetaDataDTO {
    private static final Gson gson = new Gson();
    protected static final String CONNECTION_TYPE_KEY = "CONTYPE";
    protected Map<String, String> metaData;

    public ConnectionMetaDataDTO() {
        metaData = new HashMap<>();
    }

    /**
     * Gets the connection type string that discriminates what type of acceptor this device supports.
     * @return the connection type string
     */
    public String getConnectionType() {
        final String type = metaData.get(CONNECTION_TYPE_KEY);
        if(type == null) {
            throw new NullPointerException();
        }
        return type;
    }

    /**
     * Set the acceptor type
     * @param acceptorType the acceptor type
     */
    public void setAcceptorType(String acceptorType) {
        metaData.put(CONNECTION_TYPE_KEY, acceptorType);
    }

    /**
     * Get the metadata.
     * Note that this method leaks the internal data structure and
     * changes to the instance will reflect to this object.
     *
     * @return the meta data map
     */
    public Map<String, String> getMetaData() {
        return metaData;
    }

    /**
     * Deserialize a list of ConnectionMetaDataDTO instances from a given json string
     * @param jsonString the string to deserialize
     * @return the ConnectionMetaDataDTO
     */
    public static List<ConnectionMetaDataDTO> fromJson(String jsonString) {
        return gson.fromJson(jsonString, new TypeToken<List<ConnectionMetaDataDTO>>(){}.getType());
    }

    /**
     * Get a json string serializing the given connection meta data list
     *
     * @return the json representation
     */
    public static String toJson(List<ConnectionMetaDataDTO> connectionMetaDataDTOList) {
        return gson.toJson(connectionMetaDataDTOList);
    }

    @Override
    public String toString() {
        return metaData.toString();
    }
}
