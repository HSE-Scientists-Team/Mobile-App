package org.matchlab.academic_net.feature.reviews.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.matchlab.academic_net.common.model.ProjectId
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.common.result.RemoteFailure
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraft
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError.ProjectMissing
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError.RatingMissing
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError.TextBlank
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError.TextTooLong
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError.TitleBlank
import org.matchlab.academic_net.feature.reviews.domain.ReviewDraftError.TitleTooLong
import org.matchlab.academic_net.feature.reviews.domain.ReviewRefusal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 16 "Отзыв о работе": the common projects are read at the start and on "Повторить", one project is taken at once, the
 * errors of the fields come only after a tap on the button and go with an edit of their field, one tap is one request, and
 * every way a send fails leaves the form as it is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewFormViewModelTest {
    @BeforeTest
    fun setUp() = installMainDispatcher()

    @AfterTest
    fun tearDown() = uninstallMainDispatcher()

    private val repository = FakeReviewsRepository().apply {
        onGetProjects = { listOf(project("p-1"), project("p-2")) }
    }

    private fun form() = reviewForm(repository)

    private fun ReviewFormViewModel.loaded() = assertIs<ReviewProjects.Loaded>(uiState.value.projects)

    /** The form with everything filled in and ready to send. */
    private fun filledForm(): ReviewFormViewModel = form().apply {
        onProjectSelected(ProjectId("p-1"))
        onRatingChange(4)
        onTitleChange("Отлично")
        onTextChange("Довёл проект до демо")
    }

    // ---- the projects ----

    @Test
    fun theProjectsOfTheTargetAreReadWhenTheFormIsCreated() {
        val form = form()

        assertEquals(listOf(Target), repository.projectRequests)
        assertEquals(listOf("p-1", "p-2"), form.loaded().projects.map { it.id.value })
    }

    @Test
    fun theProjectsAreAskedForTheTargetOfTheForm() {
        reviewForm(repository, UserId("somebody-else"))

        assertEquals(listOf(UserId("somebody-else")), repository.projectRequests)
    }

    @Test
    fun theFormStartsLoadingAndStatesComeInOrder() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.onGetProjects = { gate.await(); listOf(project("p-1"), project("p-2")) }
        val form = form()
        val projects = mutableListOf<ReviewProjects>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            form.uiState.collect { if (projects.lastOrNull() != it.projects) projects += it.projects }
        }

        assertEquals(listOf<ReviewProjects>(ReviewProjects.Loading), projects)
        gate.complete(Unit)

        assertEquals(2, projects.size)
        assertEquals(ReviewProjects.Loading, projects[0])
        assertIs<ReviewProjects.Loaded>(projects[1])
        assertFalse(form.uiState.value.isSubmitting)
        assertNull(form.uiState.value.failure)
    }

    @Test
    fun severalProjectsAreNotChosenForTheUser() {
        val form = form()

        assertNull(form.uiState.value.draft.projectId)
        assertNull(form.uiState.value.chosenProject)
    }

    @Test
    fun exactlyOneProjectIsChosenAtOnce() {
        repository.onGetProjects = { listOf(project("only", title = "Единственный")) }

        val form = form()

        assertEquals(ProjectId("only"), form.uiState.value.draft.projectId)
        assertEquals("Единственный", form.uiState.value.chosenProject?.title)
    }

    @Test
    fun noCommonProjectIsAnEmptyListAndNothingIsChosen() {
        repository.onGetProjects = { emptyList() }

        val form = form()

        assertEquals(emptyList(), form.loaded().projects)
        assertNull(form.uiState.value.draft.projectId)
        assertNull(form.uiState.value.chosenProject)
    }

    @Test
    fun aFailedReadIsFailedWithItsCause() {
        val cause = RemoteFailure.NoConnection()
        repository.onGetProjects = { throw cause }

        val form = form()

        assertSame(cause, assertIs<ReviewProjects.Failed>(form.uiState.value.projects).cause)
        assertNull(form.uiState.value.draft.projectId)
    }

    @Test
    fun retryAfterAFailureReadsAgainAndThenTheProjectsAreThere() {
        repository.onGetProjects = { throw RemoteFailure.Timeout() }
        val form = form()
        repository.onGetProjects = { listOf(project("p-1"), project("p-2")) }

        form.onRetryProjects()

        assertEquals(2, repository.projectRequests.size)
        assertEquals(2, form.loaded().projects.size)
    }

    @Test
    fun retryShowsLoadingWhileItRunsAndASecondTapDoesNothing() {
        repository.onGetProjects = { throw RemoteFailure.Timeout() }
        val form = form()
        val gate = CompletableDeferred<Unit>()
        repository.onGetProjects = { gate.await(); listOf(project("p-1"), project("p-2")) }

        form.onRetryProjects()
        assertEquals(ReviewProjects.Loading, form.uiState.value.projects)
        form.onRetryProjects()
        form.onRetryProjects()
        gate.complete(Unit)

        assertEquals(2, repository.projectRequests.size, "the first read and one retry")
        assertIs<ReviewProjects.Loaded>(form.uiState.value.projects)
    }

    @Test
    fun retryAfterAFailureCanFailAgainAndAskAgain() {
        repository.onGetProjects = { throw RemoteFailure.Timeout() }
        val form = form()

        form.onRetryProjects()
        assertIs<ReviewProjects.Failed>(form.uiState.value.projects)
        form.onRetryProjects()

        assertEquals(3, repository.projectRequests.size)
    }

    @Test
    fun retryWhenTheProjectsAreLoadedAsksNothing() {
        val form = form()

        form.onRetryProjects()

        assertEquals(1, repository.projectRequests.size)
        assertIs<ReviewProjects.Loaded>(form.uiState.value.projects)
    }

    @Test
    fun retryWhileTheFirstReadRunsAsksNothing() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetProjects = { gate.await(); listOf(project("p-1")) }
        val form = form()

        form.onRetryProjects()
        gate.complete(Unit)

        assertEquals(1, repository.projectRequests.size)
    }

    @Test
    fun theOnlyProjectOfARetryIsChosenToo() {
        repository.onGetProjects = { throw RemoteFailure.NoConnection() }
        val form = form()
        repository.onGetProjects = { listOf(project("only")) }

        form.onRetryProjects()

        assertEquals(ProjectId("only"), form.uiState.value.draft.projectId)
    }

    @Test
    fun theOnlyProjectTakesBackTheErrorOfAMissingProject() {
        // The user tapped the button while the projects were still loading: the project was missing then.
        val gate = CompletableDeferred<Unit>()
        repository.onGetProjects = { gate.await(); listOf(project("only")) }
        val form = form()
        form.onSubmit()
        assertTrue(ProjectMissing in form.uiState.value.errors)

        gate.complete(Unit)

        assertFalse(ProjectMissing in form.uiState.value.errors)
        assertTrue(RatingMissing in form.uiState.value.errors, "the other errors stay")
    }

    // ---- the project ----

    @Test
    fun aProjectOfTheListIsChosen() {
        val form = form()

        form.onProjectSelected(ProjectId("p-2"))

        assertEquals(ProjectId("p-2"), form.uiState.value.draft.projectId)
        assertEquals("Проект p-2", form.uiState.value.chosenProject?.title)
    }

    @Test
    fun theUserCanChooseAnotherProjectWhenOnlyOneWasOffered() {
        repository.onGetProjects = { listOf(project("p-1")) }
        val form = form()

        form.onProjectSelected(ProjectId("p-1"))

        assertEquals(ProjectId("p-1"), form.uiState.value.draft.projectId)
    }

    @Test
    fun aProjectOutsideTheListIsIgnored() {
        val form = form()
        form.onProjectSelected(ProjectId("p-1"))

        form.onProjectSelected(ProjectId("ghost"))

        assertEquals(ProjectId("p-1"), form.uiState.value.draft.projectId, "the old choice stays")
    }

    @Test
    fun aProjectOutsideTheListIsIgnoredWhenNothingIsChosenToo() {
        val form = form()

        form.onProjectSelected(ProjectId("ghost"))

        assertNull(form.uiState.value.draft.projectId)
    }

    @Test
    fun noProjectIsChosenBeforeTheListIsThere() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetProjects = { gate.await(); listOf(project("p-1"), project("p-2")) }
        val form = form()

        form.onProjectSelected(ProjectId("p-1"))

        assertNull(form.uiState.value.draft.projectId)
        gate.complete(Unit)
        assertNull(form.uiState.value.draft.projectId)
    }

    @Test
    fun noProjectIsChosenAfterAFailedRead() {
        repository.onGetProjects = { throw RemoteFailure.NoConnection() }
        val form = form()

        form.onProjectSelected(ProjectId("p-1"))

        assertNull(form.uiState.value.draft.projectId)
    }

    @Test
    fun choosingAProjectTakesBackItsErrorOnly() {
        val form = form()
        form.onSubmit()
        assertEquals(setOf(ProjectMissing, RatingMissing, TitleBlank, TextBlank), form.uiState.value.errors)

        form.onProjectSelected(ProjectId("p-1"))

        assertEquals(setOf(RatingMissing, TitleBlank, TextBlank), form.uiState.value.errors)
    }

    // ---- the errors of the fields ----

    @Test
    fun anEmptyFormShowsNoErrorBeforeTheFirstTap() {
        val form = form()
        form.onTitleChange("")
        form.onTextChange("   ")

        assertEquals(emptySet(), form.uiState.value.errors)
    }

    @Test
    fun theTapOnAnEmptyFormNamesEveryFieldAndSendsNothing() {
        val form = form()

        form.onSubmit()

        assertEquals(setOf(ProjectMissing, RatingMissing, TitleBlank, TextBlank), form.uiState.value.errors)
        assertEquals(emptyList(), repository.sent)
        assertFalse(form.uiState.value.isSubmitting, "the form is open again")
        assertFalse(form.uiState.value.isSent)
        assertNull(form.uiState.value.failure)
    }

    @Test
    fun blankTextsAreBlankFields() {
        val form = form()
        form.onProjectSelected(ProjectId("p-1"))
        form.onRatingChange(3)
        form.onTitleChange("   \n ")
        form.onTextChange("\t")

        form.onSubmit()

        assertEquals(setOf(TitleBlank, TextBlank), form.uiState.value.errors)
        assertEquals(emptyList(), repository.sent)
    }

    @Test
    fun anErrorGoesWhenItsOwnFieldIsEditedAndNotBefore() {
        val form = form()
        form.onSubmit()

        form.onTitleChange("З")
        assertEquals(setOf(ProjectMissing, RatingMissing, TextBlank), form.uiState.value.errors)
        form.onTextChange("К")
        assertEquals(setOf(ProjectMissing, RatingMissing), form.uiState.value.errors)
        form.onRatingChange(2)
        assertEquals(setOf(ProjectMissing), form.uiState.value.errors)
        form.onProjectSelected(ProjectId("p-2"))
        assertEquals(emptySet(), form.uiState.value.errors)
    }

    @Test
    fun editingATitleToBlankTakesTheErrorBackAndTheNextTapFindsItAgain() {
        val form = filledForm()
        form.onTitleChange("")
        form.onSubmit()
        assertEquals(setOf(TitleBlank), form.uiState.value.errors)

        form.onTitleChange("Снова")
        assertEquals(emptySet(), form.uiState.value.errors)
        form.onTitleChange("  ")
        assertEquals(emptySet(), form.uiState.value.errors, "typing does not check")
        form.onSubmit()

        assertEquals(setOf(TitleBlank), form.uiState.value.errors)
    }

    @Test
    fun ratingZeroDoesNotTakeTheRatingErrorBack() {
        val form = form()
        form.onSubmit()

        form.onRatingChange(0)
        assertTrue(RatingMissing in form.uiState.value.errors)
        form.onRatingChange(1)

        assertFalse(RatingMissing in form.uiState.value.errors)
    }

    @Test
    fun takingTheMarkBackAfterTheErrorWentLeavesNoErrorUntilTheNextTap() {
        val form = filledForm()
        form.onRatingChange(0)

        assertEquals(emptySet(), form.uiState.value.errors)
        form.onSubmit()
        assertEquals(setOf(RatingMissing), form.uiState.value.errors)
    }

    @Test
    fun aTooLongTitleAndATooLongCommentAreNamedAndEditsTakeThemBack() {
        val form = filledForm()
        form.onTitleChange("а".repeat(256))
        form.onTextChange("б".repeat(10_001))

        form.onSubmit()

        assertEquals(setOf(TitleTooLong, TextTooLong), form.uiState.value.errors)
        assertEquals(emptyList(), repository.sent)
        form.onTitleChange("а".repeat(255))
        assertEquals(setOf(TextTooLong), form.uiState.value.errors)
        form.onTextChange("б".repeat(10_000))
        assertEquals(emptySet(), form.uiState.value.errors)
    }

    @Test
    fun theLimitsAreInclusive() {
        val form = filledForm()
        form.onTitleChange("а".repeat(255))
        form.onTextChange("б".repeat(10_000))

        form.onSubmit()

        assertEquals(1, repository.sent.size)
        assertTrue(form.uiState.value.isSent)
    }

    @Test
    fun theLimitsCountCodePointsSoEmojiFillThemOnePerCharacter() {
        val form = filledForm()
        // 255 emoji are 510 units of a Kotlin string, and the server takes them.
        form.onTitleChange(emojis(255))
        form.onTextChange(emojis(10_000))

        form.onSubmit()

        assertEquals(1, repository.sent.size, "a count by UTF-16 units would have refused it")
    }

    @Test
    fun oneEmojiOverTheLimitIsTooLong() {
        val form = filledForm()
        form.onTitleChange(emojis(256))
        form.onTextChange(emojis(10_001))

        form.onSubmit()

        assertEquals(setOf(TitleTooLong, TextTooLong), form.uiState.value.errors)
        assertEquals(emptyList(), repository.sent)
    }

    @Test
    fun spacesAroundTheTitleDoNotCountTowardTheLimit() {
        val form = filledForm()
        form.onTitleChange("  " + "а".repeat(255) + "\n ")
        form.onTextChange(" " + "б".repeat(10_000) + " ")

        form.onSubmit()

        assertEquals(1, repository.sent.size)
    }

    // ---- the mark ----

    @Test
    fun theMarkIsLimitedToZeroToFive() {
        val form = form()

        form.onRatingChange(7)
        assertEquals(5, form.uiState.value.draft.rating)
        form.onRatingChange(Int.MAX_VALUE)
        assertEquals(5, form.uiState.value.draft.rating)
        form.onRatingChange(-3)
        assertEquals(0, form.uiState.value.draft.rating)
        form.onRatingChange(Int.MIN_VALUE)
        assertEquals(0, form.uiState.value.draft.rating)
        for (mark in 0..5) {
            form.onRatingChange(mark)
            assertEquals(mark, form.uiState.value.draft.rating)
        }
    }

    @Test
    fun aMarkAboveFiveIsSentAsFive() {
        val form = filledForm()
        form.onRatingChange(9)

        form.onSubmit()

        assertEquals(5, repository.sent.single().second.rating)
    }

    // ---- the counters ----

    @Test
    fun theCountersCountTheTrimmedTextInCodePoints() {
        val form = form()

        form.onTitleChange("  Отлично \n")
        form.onTextChange("a" + emojis(3) + "  ")

        assertEquals(7, form.uiState.value.titleLength)
        assertEquals(4, form.uiState.value.textLength)
    }

    @Test
    fun theCountersAreZeroForBlankFields() {
        val form = form()
        form.onTitleChange("   ")

        assertEquals(0, form.uiState.value.titleLength)
        assertEquals(0, form.uiState.value.textLength)
    }

    @Test
    fun theCountersAgreeWithTheDomainsLength() {
        val form = form()
        form.onTitleChange(" ${emojis(2)}x ")
        form.onTextChange("${emojis(5)}\uD83D") // a lone high surrogate at the end

        val state = form.uiState.value
        assertEquals(state.draft.titleLength, state.titleLength)
        assertEquals(state.draft.textLength, state.textLength)
        assertEquals(6, state.textLength, "a lone surrogate is one character")
    }

    @Test
    fun aLoneSurrogateIsOneCodePointWhereverItStands() {
        fun length(text: String) = ReviewDraft(title = text).titleLength
        assertEquals(1, length("\uD83D"))
        assertEquals(1, length("\uDE00"))
        assertEquals(2, length("\uDE00\uD83D"), "a low then a high is no pair")
        assertEquals(3, length("\uD83Da\uDE00"))
        assertEquals(1, length(emojis(1)))
        assertEquals(0, length(""))
    }

    @Test
    fun theOverLimitFlagsTurnOnAboveTheLimitsOnly() {
        val form = form()

        form.onTitleChange("а".repeat(255))
        form.onTextChange("б".repeat(10_000))
        assertFalse(form.uiState.value.isTitleOverLimit)
        assertFalse(form.uiState.value.isTextOverLimit)

        form.onTitleChange("а".repeat(256))
        form.onTextChange("б".repeat(10_001))
        assertTrue(form.uiState.value.isTitleOverLimit)
        assertTrue(form.uiState.value.isTextOverLimit)
    }

    @Test
    fun theOverLimitFlagsCountCodePointsAndIgnoreSpacesAroundTheText() {
        val form = form()

        form.onTitleChange(emojis(255))
        form.onTextChange(emojis(10_000))
        assertEquals(255, form.uiState.value.titleLength)
        assertFalse(form.uiState.value.isTitleOverLimit, "510 units are 255 characters")
        assertFalse(form.uiState.value.isTextOverLimit)

        form.onTitleChange(emojis(256))
        form.onTextChange(emojis(10_001))
        assertTrue(form.uiState.value.isTitleOverLimit)
        assertTrue(form.uiState.value.isTextOverLimit)

        form.onTitleChange("  " + "а".repeat(255) + "   ")
        form.onTextChange("\n" + "б".repeat(10_000) + " ")
        assertFalse(form.uiState.value.isTitleOverLimit)
        assertFalse(form.uiState.value.isTextOverLimit)
    }

    @Test
    fun theFlagsAreOffOnAFreshForm() {
        val state = form().uiState.value

        assertFalse(state.isTitleOverLimit)
        assertFalse(state.isTextOverLimit)
    }

    // ---- sending ----

    @Test
    fun aValidFormSendsTheTrimmedDraftOfTheTargetOnce() {
        val form = form()
        form.onProjectSelected(ProjectId("p-2"))
        form.onRatingChange(5)
        form.onTitleChange("  Отлично ")
        form.onTextChange("\nДовёл до демо  ")

        form.onSubmit()

        assertEquals(
            listOf(Target to ReviewDraft(ProjectId("p-2"), 5, "Отлично", "Довёл до демо")),
            repository.sent,
        )
        assertTrue(form.uiState.value.isSent)
        assertEquals(emptySet(), form.uiState.value.errors)
        assertNull(form.uiState.value.failure)
    }

    @Test
    fun theGuardHoldsAfterASuccessAndATapThenSendsNothing() {
        val form = filledForm()
        form.onSubmit()
        assertTrue(form.uiState.value.isSent)
        assertTrue(form.uiState.value.isSubmitting, "the screen is closing: the form stays busy")

        form.onSubmit()
        form.onSubmit()

        assertEquals(1, repository.sent.size)
    }

    @Test
    fun fieldsAreFrozenAfterASuccess() {
        val form = filledForm()
        form.onSubmit()
        val sent = form.uiState.value

        form.onTitleChange("Другой")
        form.onTextChange("Другой")
        form.onRatingChange(1)
        form.onProjectSelected(ProjectId("p-2"))

        assertEquals(sent, form.uiState.value)
    }

    @Test
    fun manyTapsWhileTheRequestRunsAreOneRequest() {
        val gate = CompletableDeferred<Unit>()
        repository.onSend = { _, _ -> gate.await() }
        val form = filledForm()

        repeat(5) { form.onSubmit() }
        assertTrue(form.uiState.value.isSubmitting)
        assertFalse(form.uiState.value.isSent)
        gate.complete(Unit)

        assertEquals(1, repository.sent.size)
        assertTrue(form.uiState.value.isSent)
        assertTrue(form.uiState.value.isSubmitting)
    }

    @Test
    fun fieldsAreFrozenWhileTheRequestRuns() {
        val gate = CompletableDeferred<Unit>()
        repository.onSend = { _, _ -> gate.await() }
        val form = filledForm()
        form.onSubmit()
        val busy = form.uiState.value

        form.onTitleChange("Другой")
        form.onTextChange("Другой")
        form.onRatingChange(1)
        form.onProjectSelected(ProjectId("p-2"))

        assertEquals(busy, form.uiState.value)
        gate.complete(Unit)
        assertEquals(ReviewDraft(ProjectId("p-1"), 4, "Отлично", "Довёл проект до демо"), repository.sent.single().second)
    }

    @Test
    fun anAlreadyReviewedRefusalIsKeptAsSuchAndTheFormStays() {
        repository.onSend = { _, _ -> throw ReviewRefusal.AlreadyReviewed() }
        val form = filledForm()
        val before = form.uiState.value

        form.onSubmit()

        val state = form.uiState.value
        assertEquals(ReviewSendFailure.AlreadyReviewed, state.failure)
        assertFalse(state.isSubmitting)
        assertFalse(state.isSent)
        assertEquals(before.draft, state.draft)
        assertEquals(emptySet(), state.errors)
        assertEquals(before.projects, state.projects)
    }

    @Test
    fun aNotAllowedRefusalIsNotAllowed() {
        repository.onSend = { _, _ -> throw ReviewRefusal.NotAllowed() }
        val form = filledForm()

        form.onSubmit()

        assertEquals(ReviewSendFailure.NotAllowed, form.uiState.value.failure)
        assertFalse(form.uiState.value.isSubmitting)
        assertFalse(form.uiState.value.isSent)
    }

    @Test
    fun everyOtherFailureIsAFailedSendWithItsCause() {
        val causes = listOf(
            RemoteFailure.NoConnection(),
            RemoteFailure.Timeout(),
            RemoteFailure.Http(500),
            RemoteFailure.Http(409),
            RemoteFailure.Http(403),
            RemoteFailure.Malformed(),
            IllegalStateException("bug"),
        )
        for (cause in causes) {
            repository.onSend = { _, _ -> throw cause }
            val form = filledForm()

            form.onSubmit()

            val failure = assertIs<ReviewSendFailure.Failed>(form.uiState.value.failure, "$cause")
            assertSame(cause, failure.cause)
            assertFalse(form.uiState.value.isSubmitting)
            assertFalse(form.uiState.value.isSent)
        }
    }

    @Test
    fun aFailedSendCanBeRepeatedAndTheSecondTimeGoesThrough() {
        var attempts = 0
        repository.onSend = { _, _ -> if (attempts++ == 0) throw RemoteFailure.NoConnection() }
        val form = filledForm()

        form.onSubmit()
        assertIs<ReviewSendFailure.Failed>(form.uiState.value.failure)
        form.onSubmit()

        assertEquals(2, repository.sent.size)
        assertEquals(repository.sent[0], repository.sent[1], "the same review is sent again")
        assertNull(form.uiState.value.failure)
        assertTrue(form.uiState.value.isSent)
    }

    @Test
    fun aFailedSendCanFailAgainAndAgain() {
        repository.onSend = { _, _ -> throw RemoteFailure.Timeout() }
        val form = filledForm()

        repeat(3) { form.onSubmit() }

        assertEquals(3, repository.sent.size)
        assertIs<ReviewSendFailure.Failed>(form.uiState.value.failure)
        assertFalse(form.uiState.value.isSubmitting)
    }

    @Test
    fun theFailureIsGoneWhileTheNextRequestRuns() {
        var attempts = 0
        val gate = CompletableDeferred<Unit>()
        repository.onSend = { _, _ ->
            if (attempts++ == 0) throw RemoteFailure.NoConnection() else gate.await()
        }
        val form = filledForm()
        form.onSubmit()
        assertIs<ReviewSendFailure.Failed>(form.uiState.value.failure)

        form.onSubmit()

        assertNull(form.uiState.value.failure)
        assertTrue(form.uiState.value.isSubmitting)
        gate.complete(Unit)
    }

    @Test
    fun anyEditTakesTheFailureBack() {
        val edits = listOf<(ReviewFormViewModel) -> Unit>(
            { it.onTitleChange("Новый") },
            { it.onTextChange("Новый") },
            { it.onRatingChange(2) },
            { it.onProjectSelected(ProjectId("p-2")) },
        )
        for (edit in edits) {
            repository.onSend = { _, _ -> throw ReviewRefusal.AlreadyReviewed() }
            val form = filledForm()
            form.onSubmit()
            assertEquals(ReviewSendFailure.AlreadyReviewed, form.uiState.value.failure)

            edit(form)

            assertNull(form.uiState.value.failure)
        }
    }

    @Test
    fun aRefusedFormCanBeChangedAndSentAgain() {
        var attempts = 0
        repository.onSend = { _, _ -> if (attempts++ == 0) throw ReviewRefusal.AlreadyReviewed() }
        val form = filledForm()
        form.onSubmit()

        form.onProjectSelected(ProjectId("p-2"))
        form.onSubmit()

        assertEquals(listOf(ProjectId("p-1"), ProjectId("p-2")), repository.sent.map { it.second.projectId })
        assertTrue(form.uiState.value.isSent)
    }

    @Test
    fun anInvalidTapAfterAFailureShowsTheErrorsAndDropsTheFailure() {
        repository.onSend = { _, _ -> throw RemoteFailure.Timeout() }
        val form = filledForm()
        form.onSubmit()
        assertIs<ReviewSendFailure.Failed>(form.uiState.value.failure)
        form.onTitleChange("")
        form.onSubmit()

        assertNull(form.uiState.value.failure)
        assertEquals(setOf(TitleBlank), form.uiState.value.errors)
        assertEquals(1, repository.sent.size, "the invalid form sent nothing")
    }

    @Test
    fun anInvalidTapDoesNotBlockTheNextOne() {
        val form = form()
        form.onSubmit()
        form.onSubmit()
        form.onProjectSelected(ProjectId("p-1"))
        form.onRatingChange(3)
        form.onTitleChange("З")
        form.onTextChange("К")

        form.onSubmit()

        assertEquals(1, repository.sent.size)
        assertTrue(form.uiState.value.isSent)
    }

    @Test
    fun theErrorsOfAnInvalidTapAreReplacedByTheNextCheck() {
        val form = form()
        form.onSubmit()
        form.onProjectSelected(ProjectId("p-1"))
        form.onRatingChange(3)

        form.onSubmit()

        assertEquals(setOf(TitleBlank, TextBlank), form.uiState.value.errors)
    }

    @Test
    fun aTapBeforeTheProjectsAreReadIsInvalidForTheProjectAndSendsNothing() {
        val gate = CompletableDeferred<Unit>()
        repository.onGetProjects = { gate.await(); listOf(project("p-1"), project("p-2")) }
        val form = form()
        form.onRatingChange(3)
        form.onTitleChange("З")
        form.onTextChange("К")

        form.onSubmit()

        assertEquals(setOf(ProjectMissing), form.uiState.value.errors)
        assertEquals(emptyList(), repository.sent)
        gate.complete(Unit)
    }

    @Test
    fun aFailedReadOfTheProjectsStopsSendingToo() {
        repository.onGetProjects = { throw RemoteFailure.NoConnection() }
        val form = form()
        form.onRatingChange(3)
        form.onTitleChange("З")
        form.onTextChange("К")

        form.onSubmit()

        assertEquals(setOf(ProjectMissing), form.uiState.value.errors)
        assertEquals(emptyList(), repository.sent)
    }

    @Test
    fun sendingDoesNotTouchTheProjectsOfThePicker() {
        val form = filledForm()
        val projects = form.uiState.value.projects

        form.onSubmit()

        assertEquals(projects, form.uiState.value.projects)
        assertEquals(1, repository.projectRequests.size)
    }

    @Test
    fun everyErrorTheDomainNamesGoesWithAnEditOfItsField() {
        // Every error the domain can name is one the form clears by editing a field: no error stays forever.
        val form = form()
        form.onSubmit()
        form.onTitleChange("а".repeat(256))
        form.onTextChange("б".repeat(10_001))
        form.onProjectSelected(ProjectId("p-1"))
        form.onRatingChange(1)
        form.onSubmit()
        assertEquals(setOf(TitleTooLong, TextTooLong), form.uiState.value.errors)

        form.onTitleChange("з")
        form.onTextChange("к")

        assertEquals(emptySet<ReviewDraftError>(), form.uiState.value.errors)
    }
}
