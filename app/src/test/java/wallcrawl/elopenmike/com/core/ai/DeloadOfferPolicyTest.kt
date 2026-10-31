package wallcrawl.elopenmike.com.core.ai

import org.junit.Assert.*
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

class DeloadOfferPolicyTest {
    private val profile = UserProfile(onboardingCompleted = true, returningAfterBreakWeeks = 12)
    private val preferences = DeloadPreferences()

    @Test fun supportedPersistedBreaksKeepStableOffersAndHandledKeysBeyondTheInteractiveEditLimit() {
        for (weeks in listOf(520, 521, 5_200)) {
            val restored = profile.copy(returningAfterBreakWeeks = weeks)
            val key = "${DeloadOfferPolicy.VERSION}:${profile.id}:$weeks"
            val offer = requireNotNull(DeloadOfferPolicy.offer(restored, preferences))
            assertEquals(key, offer.id)
            assertEquals(key, DeloadOfferPolicy.returnKey(restored))
            val handled = DeloadPreferences(revision = 1,
                choice = DeloadChoice(offer, DeloadChoiceStatus.DECLINED, 1),
                lastHandledReturnKey = key)
            assertNull(DeloadOfferPolicy.offer(restored, handled))
        }
    }

    @Test fun returnKeysStillRejectValuesBeyondThePersistedRangeWithoutEchoingThem() {
        assertNull(DeloadOfferPolicy.returnKey(profile.copy(returningAfterBreakWeeks = 0)))
        val invalidKey = "${DeloadOfferPolicy.VERSION}:${profile.id}:5201"
        val error = assertThrows(IllegalArgumentException::class.java) {
            DeloadPreferences(revision = 1, lastHandledReturnKey = invalidKey)
        }
        assertFalse(error.message.orEmpty().contains(invalidKey))
        for (weeks in listOf(-1, 5_201)) {
            assertThrows(IllegalArgumentException::class.java) {
                DeloadOfferPolicy.returnKey(profile.copy(returningAfterBreakWeeks = weeks))
            }
        }
    }

    @Test fun returningOfferIsStablePureAndSuppressedOnlyForTheHandledKey() {
        val offer = requireNotNull(DeloadOfferPolicy.offer(profile, preferences))
        assertEquals(DeloadSource.RETURNING, offer.source)
        assertEquals(DeloadOfferPolicy.VERSION, offer.policyVersion)
        assertEquals(DeloadOfferPolicy.returnKey(profile), offer.id)
        assertEquals(offer, DeloadOfferPolicy.offer(profile.copy(revision = 9, name = "Changed"), preferences))
        val handled = preferences.copy(revision = 1,
            choice = DeloadChoice(offer, DeloadChoiceStatus.DECLINED, 1),
            lastHandledReturnKey = offer.id)
        assertNull(DeloadOfferPolicy.offer(profile, handled))
        assertNotNull(DeloadOfferPolicy.offer(profile.copy(returningAfterBreakWeeks = 26), handled))
        assertNull(DeloadOfferPolicy.offer(profile.copy(returningAfterBreakWeeks = 0), preferences))
        assertNull(DeloadOfferPolicy.offer(profile.copy(onboardingCompleted = false), preferences))
        assertEquals(DeloadPreferences(), preferences)
    }

    @Test fun explicitRequestWinsAndAcceptanceSurvivesProfileEditsWithoutExpiry() {
        val offer = DeloadOffer("request-1", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        val offered = preferences.copy(revision = 1, choice = DeloadChoice(offer, DeloadChoiceStatus.OFFERED, 1))
        assertEquals(offer, DeloadOfferPolicy.offer(profile, offered))
        assertNull(DeloadOfferPolicy.accepted(offered))
        val accepted = offered.copy(revision = 2, choice = offered.choice!!.copy(status = DeloadChoiceStatus.ACCEPTED))
        assertEquals(accepted.choice, DeloadOfferPolicy.accepted(accepted))
        assertNull(DeloadOfferPolicy.offer(profile.copy(returningAfterBreakWeeks = 0, revision = 5), accepted))
        assertNull(DeloadOfferPolicy.offer(profile.copy(returningAfterBreakWeeks = 52), accepted))
        val consumed = accepted.copy(revision = 3,
            choice = accepted.choice!!.copy(status = DeloadChoiceStatus.CONSUMED, sessionId = "session"))
        assertNull(DeloadOfferPolicy.accepted(consumed))
    }

    @Test fun invalidStatesBoundsAndIdentifiersRejectWithoutEchoingValues() {
        val offer = DeloadOffer("request", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        listOf("", "secret\nvalue", "secret|||value", "x".repeat(257)).forEach { id ->
            val error = assertThrows(IllegalArgumentException::class.java) { offer.copy(id = id) }
            if (id.isNotEmpty()) assertFalse(error.message.orEmpty().contains(id))
        }
        assertThrows(IllegalArgumentException::class.java) { offer.copy(policyVersion = "unknown") }
        assertThrows(IllegalArgumentException::class.java) { DeloadChoice(offer, DeloadChoiceStatus.CONSUMED, 1) }
        assertThrows(IllegalArgumentException::class.java) { DeloadChoice(offer, DeloadChoiceStatus.ACCEPTED, 1, "session") }
        assertThrows(IllegalArgumentException::class.java) { DeloadChoice(offer, DeloadChoiceStatus.ACCEPTED, -1) }
        assertThrows(IllegalArgumentException::class.java) { DeloadPreferences(revision = -1) }
        assertThrows(IllegalArgumentException::class.java) { DeloadPreferences(choice = DeloadChoice(offer, DeloadChoiceStatus.ACCEPTED, 1)) }
        assertThrows(IllegalArgumentException::class.java) { DeloadPreferences(lastHandledReturnKey = "garbage") }
        assertThrows(IllegalArgumentException::class.java) {
            DeloadChoice(DeloadOffer("return", DeloadSource.RETURNING, DeloadOfferPolicy.VERSION), DeloadChoiceStatus.OFFERED, 1)
        }
    }
}
