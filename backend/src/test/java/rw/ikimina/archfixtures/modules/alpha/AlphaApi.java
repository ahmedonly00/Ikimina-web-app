package rw.ikimina.archfixtures.modules.alpha;

import rw.ikimina.archfixtures.modules.alpha.internal.AlphaSecret;
import rw.ikimina.archfixtures.modules.beta.BetaService;

/** Allowed: a module using its own internals. Also half of an alpha <-> beta cycle. */
public class AlphaApi {

    AlphaSecret secret;
    BetaService beta;
}
