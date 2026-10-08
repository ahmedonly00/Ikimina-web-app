package rw.ikimina.archfixtures.modules.beta;

import rw.ikimina.archfixtures.modules.alpha.AlphaApi;
import rw.ikimina.archfixtures.modules.alpha.internal.AlphaSecret;

/** Violation: reaches into another module's internals. Also the other half of the cycle. */
public class BetaService {

    AlphaSecret stolen;
    AlphaApi alpha;
}
