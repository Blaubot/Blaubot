package de.hsrm.blaubot.core;

public interface IBlaubotCallback {
	
	public void onSuccess();
	
	public void onFailure(Throwable cause);

}
