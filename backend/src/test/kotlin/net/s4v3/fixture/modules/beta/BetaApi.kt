package net.s4v3.fixture.modules.beta

import net.s4v3.fixture.modules.alpha.AlphaApi
import net.s4v3.fixture.modules.alpha.internal.AlphaInternal

/** Uses alpha's internal type and alpha's API, closing the cycle. */
class BetaApi(val internal: AlphaInternal, val alpha: AlphaApi)
