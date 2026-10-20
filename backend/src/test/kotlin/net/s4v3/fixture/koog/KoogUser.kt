package net.s4v3.fixture.koog

import ai.koog.fake.FakeKoogClient

/** Deliberate violation: a class outside `llm` that depends on a Koog package. */
class KoogUser(val client: FakeKoogClient = FakeKoogClient())
