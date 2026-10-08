package rw.ikimina.archfixtures.bad;

import java.math.BigDecimal;

public class FloatingPointConversion {

    public BigDecimal tenCents() {
        return BigDecimal.valueOf(0.1);
    }

    public BigDecimal alsoTenCents() {
        return new BigDecimal(0.1);
    }
}
