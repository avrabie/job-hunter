package net.s4v3.fixture.modules.alpha

import net.s4v3.fixture.modules.beta.BetaApi

/** Depends on beta, while beta depends on alpha: a cycle. */
class AlphaApi(val beta: BetaApi)
