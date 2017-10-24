package org.openhab.binding.tado.internal.protocol;

public class OverlayState {
    private ZoneSetting setting;
    private String type;

    public ZoneSetting getSetting() {
        return setting;
    }

    public void setSetting(ZoneSetting setting) {
        this.setting = setting;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ManualTerminationInfo getTermination() {
        return termination;
    }

    public void setTermination(ManualTerminationInfo termination) {
        this.termination = termination;
    }

    private ManualTerminationInfo termination;
}
