/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tado.handler;

import static org.openhab.binding.tado.tadoBindingConstants.*;

import java.math.BigDecimal;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.tado.internal.protocol.AuthResponse;
import org.openhab.binding.tado.internal.protocol.Home;
import org.openhab.binding.tado.internal.protocol.ManualTerminationInfo;
import org.openhab.binding.tado.internal.protocol.OverlayState;
import org.openhab.binding.tado.internal.protocol.Temperature;
import org.openhab.binding.tado.internal.protocol.Zone;
import org.openhab.binding.tado.internal.protocol.ZoneSetting;
import org.openhab.binding.tado.internal.protocol.ZoneState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

/**
 * The {@link tadoThermostatHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Ben Woodford - Initial contribution
 * @author bennYx0x - Refactoring for the new Tado public preview API and adding new functionality
 */
public class tadoThermostatHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(tadoThermostatHandler.class);

    protected static String accessToken;
    protected static String refreshToken;
    protected static long tokenExpiration = 0;

    // Initialize with default values at startup
    protected DecimalType targetTemperature;
    protected DecimalType terminationTimer;
    protected OnOffType heatingState = OnOffType.OFF;

    protected Client tadoClient = ClientBuilder.newClient();
    protected WebTarget tadoTarget = tadoClient.target(API_URI);
    protected WebTarget authTarget = tadoTarget.path(ACCESS_TOKEN_URI);
    protected WebTarget homesTarget = tadoTarget.path(API_VERSION).path(HOMES);
    protected WebTarget homeTarget = homesTarget.path(HOME_ID_PATH);
    protected WebTarget zonesTarget = homeTarget.path(ZONES);
    protected WebTarget zoneTarget = zonesTarget.path(ZONE_ID_PATH);
    protected WebTarget stateTarget = zoneTarget.path(STATE);
    protected WebTarget overlayTarget = zoneTarget.path(OVERLAY);

    private JsonParser parser = new JsonParser();
    protected Gson gson = new Gson();

    ScheduledFuture<?> updateJob;

    public tadoThermostatHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (thing.getStatus().equals(ThingStatus.ONLINE)) {
            logger.debug("Update command received for channel {}!", channelUID);
            if (channelUID.getId().equals(CHANNEL_HEATING_STATE) && command instanceof OnOffType) {
                updateHeatingState(command);
            } else if (channelUID.getId().equals(CHANNEL_TARGET_TEMPERATURE) && command instanceof DecimalType) {
                updateTargetTemperature(command);
            } else if (channelUID.getId().equals(CHANNEL_TERMINATION_TIMER) && command instanceof DecimalType) {
                updateTerminationTimer(command);
            } else if (channelUID.getId().equals(CHANNEL_INSIDE_TEMPERATURE) && command instanceof RefreshType) {
                updateService.run();
            } else if (command instanceof RefreshType) {
                // Do nothing, maybe add later the updateService for more channels.
            } else {
                logger.debug("Unsupported command {}!", command);
            }
        } else {
            logger.debug("Cannot handle command. Thing is not ONLINE.");
        }
    }

    @Override
    public void initialize() {
        // TODO: Initialize the thing. If done set status to ONLINE to indicate proper working.
        // Long running initialization should be done asynchronously in background.
        connect();

        scheduleTasks();
    }

    protected void scheduleTasks() {
        if (updateJob == null || updateJob.isCancelled()) {
            int refresh = ((BigDecimal) getConfig().get(REFRESH_INTERVAL)).intValue();

            updateJob = scheduler.scheduleAtFixedRate(updateService, 0, refresh, TimeUnit.SECONDS);
        }
    }

    private void connect() {
        logger.trace("Authenticating with Tado Servers");

        ThingStatusDetail authResult = authenticate((String) getConfig().get(EMAIL),
                (String) getConfig().get(PASSWORD));

        if (authResult != ThingStatusDetail.NONE) {
            updateStatus(ThingStatus.OFFLINE, authResult);
            return;
        }

        // TODO: Nice to have... but really needed? Right now not used at all.
        // Maybe for later at a bridge for thing discovery or setting initial settings etc.
        Home home = getHome();

        if (home != null) {
            logger.trace("Got associated home: {}", home.id);
        } else {
            logger.info("Failed to retrieve Home ID " + getConfig().get(HOME_ID));
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        Zone zone = getZone();

        if (zone != null) {
            logger.trace("Got associated zone: {}", zone.id);
        } else {
            logger.info("Failed to retrieve Zone ID " + getConfig().get(ZONE_ID));
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        ZoneState zoneState = getZoneState();

        if (zoneState != null) {
            logger.trace("Got Zone State: {}", zoneState);
            updateStatus(ThingStatus.ONLINE);
        } else {
            logger.info("Failed to retrieve Zone State for Zone " + getConfig().get(ZONE_ID));
            return;
        }

        // set default for target temperature
        boolean useCelsius = (boolean) getConfig().get(USE_CELSIUS);
        if (useCelsius) {
            targetTemperature = new DecimalType(20.0);
        } else {
            targetTemperature = new DecimalType(68.0);
        }

    }

    protected Builder prepareWebTargetRequest(WebTarget target) {
        // We substract 30 seconds from the tokenExpiration
        // then we have a 30 seconds timespan to get a new refresh token
        if (System.currentTimeMillis() > (tokenExpiration - (30 * 1000))) {
            logger.trace("Refreshing Bearer Token");
            refreshToken();
        }

        return target.request(MediaType.APPLICATION_JSON_TYPE).header("Authorization", "Bearer " + accessToken);
    }

    protected Home getHome() {
        int homeId = ((BigDecimal) getConfig().get(HOME_ID)).intValue();
        Response response = prepareWebTargetRequest(homeTarget.resolveTemplate("homeId", homeId)).get();

        logger.trace("Querying Home : Response : {} ", response.getStatusInfo());

        JsonParser parser = new JsonParser();

        Home home = gson.fromJson(parser.parse(response.readEntity(String.class)).getAsJsonObject(), Home.class);

        return home;
    }

    protected Zone getZone() {
        int homeId = ((BigDecimal) getConfig().get(HOME_ID)).intValue();
        Response response = prepareWebTargetRequest(zonesTarget.resolveTemplate("homeId", homeId)).get();

        logger.trace("Querying Home {} Zone List : Response : {} ", homeId, response.getStatusInfo());

        JsonParser parser = new JsonParser();

        Zone[] zoneArray = gson.fromJson(parser.parse(response.readEntity(String.class)).getAsJsonArray(),
                Zone[].class);

        int zoneId = ((BigDecimal) getConfig().get(ZONE_ID)).intValue();

        for (int i = 0; i < zoneArray.length; i++) {
            if (zoneArray[i].id == zoneId) {
                return zoneArray[i];
            }
        }

        return null;
    }

    protected ZoneState getZoneState() {
        int homeId = ((BigDecimal) getConfig().get(HOME_ID)).intValue();
        int zoneId = ((BigDecimal) getConfig().get(ZONE_ID)).intValue();

        Response response = prepareWebTargetRequest(
                stateTarget.resolveTemplate("homeId", homeId).resolveTemplate("zoneId", zoneId)).get();

        logger.trace("Querying Home {}, Zone {} State : Response : {} ", homeId, zoneId, response.getStatus());

        if (response.getStatus() == 200 && response.hasEntity()) {

            ZoneState zoneState = gson.fromJson(parser.parse(response.readEntity(String.class)).getAsJsonObject(),
                    ZoneState.class);

            logger.trace("Got zone state: " + zoneState);

            return zoneState;
        } else {
            return null;
        }
    }

    protected void updateStateFromZone(ZoneState zoneState) {
        logger.trace("Updating Zone State");
        boolean useCelsius = (boolean) getConfig().get(USE_CELSIUS);
        updateState(CHANNEL_MODE, new StringType(zoneState.getMode()));
        updateState(CHANNEL_LINK_STATE, zoneState.getLinkState());

        updateState(CHANNEL_HUMIDITY, new PercentType(zoneState.sensorDataPoints.humidity.percentage));
        updateState(CHANNEL_INSIDE_TEMPERATURE, new DecimalType(zoneState.getInsideTemperature(useCelsius)));

        // Keep the heatingState in sync for internal logic
        heatingState = zoneState.getHeatingState();
        updateState(CHANNEL_HEATING_STATE, heatingState);

        // If heating is off, Tado returns 0.0 as target temperature
        // But for turning the heating on, the API needs a temperature to set
        // Therefore: Keep the last retrieved targetTemp setting, worst case would
        // be out of sync till next refresh due to changes made via Mobile/Web
        DecimalType newTargetTemperature = new DecimalType(zoneState.getTargetTemperature(useCelsius));
        if (useCelsius && newTargetTemperature.intValue() != 0) {
            targetTemperature = newTargetTemperature;
            updateState(CHANNEL_TARGET_TEMPERATURE, newTargetTemperature);
        } else if (!useCelsius && newTargetTemperature.intValue() != 32) {
            targetTemperature = newTargetTemperature;
            updateState(CHANNEL_TARGET_TEMPERATURE, newTargetTemperature);
        }
    }

    private Runnable updateService = new Runnable() {
        @Override
        public void run() {
            if (getThing().getStatus() == ThingStatus.ONLINE) {
                ZoneState state = null;
                try {
                    state = getZoneState();
                } catch (Exception e) {
                    updateState(CHANNEL_SERVER_STATUS, OnOffType.OFF);
                    logger.warn("Failed to retrieve zone state");
                }

                if (state == null) {
                    updateState(CHANNEL_SERVER_STATUS, OnOffType.OFF);
                    logger.warn("Failed to retrieve zone state");
                } else {
                    updateState(CHANNEL_SERVER_STATUS, OnOffType.ON);
                    updateStateFromZone(state);
                }
            }
        }
    };

    @Override
    public void dispose() {
        logger.trace("Disposing of Tado Handler for {}", getThing().getUID());

        if (updateJob != null && !updateJob.isCancelled()) {
            updateJob.cancel(true);
            updateJob = null;
        }
    }

    public boolean refreshToken() {
        Response response = authTarget.queryParam("client_id", CLIENT_ID).queryParam("client_secret", CLIENT_SECRET)
                .queryParam("grant_type", "refresh_token").queryParam("refresh_token", refreshToken).request()
                .header("Referer", "https://my.tado.com/").post(Entity.json("{}"));

        logger.trace("Authenticating: Response : {}", response.getStatusInfo());

        if (response != null) {
            if (response.getStatus() == 200 && response.hasEntity()) {
                String responsePayload = response.readEntity(String.class);
                AuthResponse readObject = gson.fromJson(parser.parse(responsePayload).getAsJsonObject(),
                        AuthResponse.class);

                return saveToken(readObject);
            } else if (response.getStatus() == 401) {
                return false;
            } else if (response.getStatus() == 503) {
                return false;
            }
        }

        return true;
    }

    public boolean saveToken(AuthResponse result) {
        if (result.access_token == null) {
            logger.debug("Missing Access Token from Authentication/Refresh Response");
            return false;
        }
        accessToken = result.access_token;
        logger.trace("Authenticating : Setting Access Code to : {}", accessToken);

        if (result.expires_in == 0) {
            logger.debug("Missing Expiration Time from Authentication/Refresh Response");
            return false;
        }

        tokenExpiration = System.currentTimeMillis() + (result.expires_in * 1000);
        logger.trace("Authenticating : Setting Token Expiration to : {}", tokenExpiration);

        if (result.refresh_token == null) {
            logger.debug("Missing Refresh Token from Authentication or Refresh Response");
            return false;
        }

        refreshToken = result.refresh_token;
        logger.trace("Authenticating : Setting Refresh Token to : {}", refreshToken);

        return true;
    }

    protected ThingStatusDetail authenticate(String email, String password) {
        Response response = authTarget.queryParam("username", email).queryParam("password", password)
                .queryParam("client_id", CLIENT_ID).queryParam("client_secret", CLIENT_SECRET)
                .queryParam("grant_type", "password").queryParam("scope", "home.user").request()
                .header("Referer", "https://my.tado.com/").post(Entity.json("{}"));

        logger.debug("Authenticating: Response : {}", response.getStatusInfo());

        if (response != null) {
            if (response.getStatus() == 200 && response.hasEntity()) {
                String responsePayload = response.readEntity(String.class);
                AuthResponse readObject = gson.fromJson(parser.parse(responsePayload).getAsJsonObject(),
                        AuthResponse.class);

                if (!saveToken(readObject)) {
                    return ThingStatusDetail.COMMUNICATION_ERROR;
                } else {
                    return ThingStatusDetail.NONE;
                }
            } else if (response.getStatus() == 401) {
                return ThingStatusDetail.CONFIGURATION_ERROR;
            } else if (response.getStatus() == 503) {
                return ThingStatusDetail.COMMUNICATION_ERROR;
            }
        }

        return ThingStatusDetail.CONFIGURATION_ERROR;
    }

    private void updateHeatingState(Command command) {
        int homeId = ((BigDecimal) getConfig().get(HOME_ID)).intValue();
        int zoneId = ((BigDecimal) getConfig().get(ZONE_ID)).intValue();

        // Building json for PUT to Tado API
        ZoneSetting setting = new ZoneSetting();
        setting.setType(HEATING);

        // Create and define the termination json part
        ManualTerminationInfo termination = new ManualTerminationInfo();
        if (terminationTimer.doubleValue() == 0) {
            termination.setType(MANUAL_MODE);
        } else if (terminationTimer.doubleValue() == -1.0) {
            termination.setType(TADO_MODE);
        } else {
            termination.setType(TIMER_MODE);
            // Calculate the given minutes to seconds
            termination.setDurationInSeconds(terminationTimer.longValue() * 60);
        }

        // Build the wrapping json and add the beforehand created json parts
        OverlayState zoneOverlay = new OverlayState();
        zoneOverlay.setSetting(setting);
        zoneOverlay.setTermination(termination);

        // Create temperature json part and determine if we turn
        // on or off and which temperature unit is used
        if (command.equals(OnOffType.ON)) {
            heatingState = OnOffType.ON;
            boolean useCelsius = (boolean) getConfig().get(USE_CELSIUS);
            setting.setPower(POWER_ON);
            Temperature temperature = new Temperature();
            if (useCelsius) {
                temperature.setCelsius(targetTemperature.toBigDecimal());
            } else {
                temperature.setFahrenheit(targetTemperature.toBigDecimal());
            }
            setting.setTemperature(temperature);
        } else {
            heatingState = OnOffType.OFF;
            setting.setPower(POWER_OFF);
        }

        Gson gson = new Gson();
        String zoneOverlayJson = gson.toJson(zoneOverlay);

        Response response = prepareWebTargetRequest(
                overlayTarget.resolveTemplate("homeId", homeId).resolveTemplate("zoneId", zoneId))
                        .header("Content-Type", "text/plain;charset=UTF-8").header("Referer", "https://my.tado.com/")
                        .header("Mime-Type", "application/json;charset=UTF-8").put(Entity.json(zoneOverlayJson));

        if (response != null) {
            if (response.getStatus() == 200 && response.hasEntity()) {
                String responsePayload = response.readEntity(String.class);
                logger.trace(responsePayload);
            } else if (response.getStatus() == 401 && response.hasEntity()) {
                logger.warn("Configuration Error: {}", response.readEntity(String.class));
            } else if (response.getStatus() == 503 && response.hasEntity()) {
                logger.warn("Communication Error: {}", response.readEntity(String.class));
            } else {
                logger.warn("Unknown Error with Status Code: {}", response.getStatus());
            }
        }
    }

    private void updateTargetTemperature(@NonNull Command command) {
        boolean useCelsius = (boolean) getConfig().get(USE_CELSIUS);
        DecimalType newTargetTemperature = ((DecimalType) command);

        // Validation for Celsius
        if (useCelsius && newTargetTemperature.doubleValue() < 5.0) {
            logger.info(
                    "Can't set target temperature below 5 degrees Celsius. Tado doesn't allow that. Given target temperature was {}.",
                    newTargetTemperature);
            return;
        } else if (useCelsius && newTargetTemperature.doubleValue() > 25.0) {
            logger.info(
                    "Can't set target temperature above 25 degrees Celsius. Tado doesn't allow that. Given target temperature was {}.",
                    newTargetTemperature);
            return;
        }

        // Validation for Fahrenheit
        if (!useCelsius && newTargetTemperature.doubleValue() < 41.0) {
            logger.info(
                    "Can't set target temperature below 41 degrees Fahrenheit. Tado doesn't allow that. Given target temperature was {}.",
                    newTargetTemperature);
            return;
        } else if (!useCelsius && newTargetTemperature.doubleValue() > 77.0) {
            logger.info(
                    "Can't set target temperature above 77 degrees Fahrenheit. Tado doesn't allow that. Given target temperature was {}.",
                    newTargetTemperature);
            return;
        }

        logger.debug("Set internal current target temperature {} to new value {}.", targetTemperature,
                newTargetTemperature);
        // Keep our internal target temperature sync
        targetTemperature = newTargetTemperature;

        // Activate the new target temperature if the heating state is ON
        if (heatingState.equals(OnOffType.ON)) {
            logger.debug("Activate new target temperature: {} degrees.", targetTemperature);
            updateHeatingState(OnOffType.ON);
        }
    }

    private void updateTerminationTimer(@NonNull Command command) {
        DecimalType newTerminationTimer = ((DecimalType) command);

        if (newTerminationTimer.doubleValue() < 0) {
            if (newTerminationTimer.doubleValue() != -1.0) {
                logger.info(
                        "Can't set termination timer to a negative value (Except for -1, for setting a timer until the next Tado mode change occurs. Given termination timer was {}.",
                        newTerminationTimer);
                return;
            }
        }

        logger.debug("Set internal termination timer {} to new value {}.", terminationTimer, newTerminationTimer);
        // Keep our internal termination timer sync
        terminationTimer = newTerminationTimer;
    }

    private void activateTadoMode() {
        int homeId = ((BigDecimal) getConfig().get(HOME_ID)).intValue();
        int zoneId = ((BigDecimal) getConfig().get(ZONE_ID)).intValue();

        // Maybe also track the mode local, to check if its already not manual

        Response response = prepareWebTargetRequest(
                overlayTarget.resolveTemplate("homeId", homeId).resolveTemplate("zoneId", zoneId)).delete();

        // TODO better error handling
        if (response != null) {
            String responsePayload = response.readEntity(String.class);
            logger.trace(responsePayload);
        }
    }
}
