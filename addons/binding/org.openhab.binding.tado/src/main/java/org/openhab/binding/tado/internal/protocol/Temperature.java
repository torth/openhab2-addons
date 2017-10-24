package org.openhab.binding.tado.internal.protocol;

import java.math.BigDecimal;

public class Temperature {
    private BigDecimal celsius;
    private BigDecimal fahrenheit;

    public BigDecimal getValue(boolean useCelsius) {
        return useCelsius ? celsius : fahrenheit;
    }

    public BigDecimal getCelsius() {
        return celsius;
    }

    public void setCelsius(BigDecimal celsius) {
        this.celsius = celsius;
    }

    public BigDecimal getFahrenheit() {
        return fahrenheit;
    }

    public void setFahrenheit(BigDecimal fahrenheit) {
        this.fahrenheit = fahrenheit;
    }
}
