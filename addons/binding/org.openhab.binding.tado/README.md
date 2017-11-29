# Tado Thermostat Binding
Currently this binding only supports Tado radiator thermostats (mostly because I don't own an A/C unit to test the Tado A/C one with, or any of their pure thermostats). Maybe later I (or someone else) can add that in though.

## Setup
Setup is a bit complicated, as you have to look for your Home ID and Zone ID. The easiest way to find your Home ID is to open up [https://my.tado.com](https://my.tado.com) and login. Once you've done so, open up the developer tools for your browser (F12 in Chrome for example) and go to the Network tab. Refresh the page and it should fill with requests.

One of these requests will have a large number in the URL. For instance /api/v2/homes/11111. That 11111 is your Home ID, note it down for later.

The Zone ID is easier, by default the web app will load up your first zone. The Zone ID is in the URL as (for example) /home/zone/1. The 1 is your Zone ID, note that down too.

The syntax for the Things file is as follows:

````
tado:thermostat:bedroom [ email="EMAIL", password="PASSWORD", homeId=HOMEID, zoneId=ZONEID, useCelsius=true ]
````
````
optional parameter for the thing: refreshInterval=x , where x in seconds
````
If you'd prefer to use Fahrenheit set useCelsius to false, but I've only tested this in Celsius so no promises it'll work well.

Enter your email and password for Tado, along with Home and Zone IDs. Set the 'home' bit of the Thing to denote this Thing's ID. 

A sample item list can be found below.

````
String Mode "Mode" { channel="tado:thermostat:bedroom:mode" }
Number Humidity "Humidity" { channel="tado:thermostat:bedroom:humidity" }
Number InsideTemperature "Inside Temperature" { channel="tado:thermostat:bedroom:insideTemperature" }
Number OutsideTemperature "Outside Temperature" { channel="tado:thermostat:bedroom:outsideTemperature" }
Number SolarIntensity "Solar Intensity" { channel="tado:thermostat:bedroom:solarIntensity" }
String WeatherState "Weather State" { channel="tado:thermostat:bedroom:weatherState" }
String LinkState "Link State" { channel="tado:thermostat:bedroom:linkState" }
String HeatingState "Heating State" { channel="tado:thermostat:bedroom:heatingState" }
Number TargetTemperature "Target Temperature" { channel="tado:thermostat:bedroom:targetTemperature" }
Number TerminationTimer "Termination Timer for Manual Mode" { channel="tado:thermostat:bedroom:terminationTimer" }
Switch ServerStatus "Server Status" { channel="tado:thermostat:bedroom:serverStatus" }
````

Information regarding Termination Timer:
  - Number value in Minutes
  - Value 0 represents infinite (which is the default value for the item)
  - Value -1 represents termination of Manual Mode until the next change of Tado Mode

## Issues

Please report any issues to [my GitHub repo](https://github.com/mebe1012/openhab2-addons/) and be sure to label the Issue as 'tado'.
