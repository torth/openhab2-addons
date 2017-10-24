package org.openhab.binding.tado.internal.protocol;

public class ManualTerminationInfo {
    // Use Long instead of long for default null, due to json creation
    private Long durationInSeconds;
    private Long remainingTimeInSeconds;
    private String expiry;
    private String projectedExpiry;
    private String type;

    public Long getDurationInSeconds() {
        return durationInSeconds;
    }

    public void setDurationInSeconds(Long durationInSeconds) {
        this.durationInSeconds = durationInSeconds;
    }

    public Long getRemainingTimeInSeconds() {
        return remainingTimeInSeconds;
    }

    public void setRemainingTimeInSeconds(Long remainingTimeInSeconds) {
        this.remainingTimeInSeconds = remainingTimeInSeconds;
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    public String getProjectedExpiry() {
        return projectedExpiry;
    }

    public void setProjectedExpiry(String projectedExpiry) {
        this.projectedExpiry = projectedExpiry;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
