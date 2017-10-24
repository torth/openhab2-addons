package org.openhab.binding.tado.internal.protocol;

public class ZoneSetting {
    private String power;
    private Temperature temperature;
    private String type;

    public String getPower() {
        return power;
    }

    public void setPower(String power) {
        this.power = power;
    }

    public Temperature getTemperature() {
        return temperature;
    }

    public void setTemperature(Temperature temperature) {
        this.temperature = temperature;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
