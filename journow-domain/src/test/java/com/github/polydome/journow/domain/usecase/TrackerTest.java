package com.github.polydome.journow.domain.usecase;

import com.github.polydome.journow.domain.controller.Tracker;
import com.github.polydome.journow.domain.exception.NoSuchTaskException;
import com.github.polydome.journow.domain.exception.TrackerNotRunningException;
import com.github.polydome.journow.domain.model.Session;
import com.github.polydome.journow.domain.model.Task;
import com.github.polydome.journow.domain.model.TrackerData;
import com.github.polydome.journow.domain.repository.SessionRepository;
import com.github.polydome.journow.domain.repository.TaskRepository;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static com.github.polydome.journow.test.TaskFactory.createTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class TrackerTest {
    TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
    MemoryTrackerDataStorage trackerDataStorage = new MemoryTrackerDataStorage();
    Clock clock = Mockito.mock(Clock.class);
    SessionRepository sessionRepository = Mockito.mock(SessionRepository.class);
    Tracker SUT = new Tracker(taskRepository, trackerDataStorage, clock, sessionRepository);

    @Test
    public void start_taskNotExists_throwsNoSuchTaskException() {
        long TASK_ID = 15;
        Mockito.when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

        NoSuchTaskException exception = assertThrows(NoSuchTaskException.class, () ->
                SUT.start(TASK_ID));

        assertThat(exception.getMessage(), equalTo(String.format("Task identified with [id=%d] does not exist", TASK_ID)));
    }

    @Test
    public void start_taskExists_savesData() {
        // given
        Instant now = Instant.ofEpochMilli(12000000);
        long TASK_ID = 15;
        when(clock.instant()).thenReturn(now);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(createTask(TASK_ID, "test task")));

        // when
        SUT.start(TASK_ID);

        // then
        assertThat(trackerDataStorage.read().isPresent(), equalTo(true));
        TrackerData actual = trackerDataStorage.read().get();
        assertThat(actual.getStartTime(), equalTo(now));
        assertThat(actual.getTaskId(), equalTo(TASK_ID));
    }

    @Test
    public void stop_trackerRunning_savesSession() {
        // given
        Instant start = Instant.ofEpochMilli(600000);
        Instant now = Instant.ofEpochMilli(12000000);
        Task task = createTask(15, "test task");
        when(clock.instant()).thenReturn(start, now);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        // when
        SUT.start(task.getId());
        SUT.stop();

        // then
        ArgumentCaptor<Session> sessionCpt = ArgumentCaptor.forClass(Session.class);

        verify(sessionRepository).insert(sessionCpt.capture());

        Session actual = sessionCpt.getValue();
        assertThat(actual.getStartedAt(), equalTo(start));
        assertThat(actual.getEndedAt(), equalTo(now));
        assertThat(actual.getTask(), equalTo(task));
    }

    @Test
    public void stop_trackerRunning_clearsData() {
        Task task = createTask(15, "test task");
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        SUT.start(task.getId());
        SUT.stop();

        assertThat(trackerDataStorage.read(), equalTo(Optional.empty()));
    }

    @Test
    public void stop_storedTaskNotExists_saveAnonymousSession() {
        // given
        Instant start = Instant.ofEpochMilli(600000);
        Instant now = Instant.ofEpochMilli(12000000);
        long TASK_ID = 15;
        when(clock.instant()).thenReturn(start, now);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(createTask(TASK_ID, "test task")));

        // when
        SUT.start(TASK_ID);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());
        SUT.stop();

        // then
        ArgumentCaptor<Session> sessionCpt = ArgumentCaptor.forClass(Session.class);

        verify(sessionRepository).insert(sessionCpt.capture());

        Session actual = sessionCpt.getValue();
        assertThat(actual.getStartedAt(), equalTo(start));
        assertThat(actual.getEndedAt(), equalTo(now));
        assertThat(actual.getTask(), equalTo(null));
    }

    @Test
    public void stop_dataEmpty_throwsTrackerNotRunningException() {
        when(trackerDataStorage.read()).thenReturn(Optional.empty());

        assertThrows(TrackerNotRunningException.class, () -> SUT.stop());
    }

    @Test
    public void currentTask_trackerNotStarted_emitsNothing() {
        SUT.currentTask().test().assertEmpty();
    }

    @Test
    public void currentTask_trackerStarted_emitsStartedTask() {
        // given
        Instant now = Instant.ofEpochMilli(12000000);
        Task task = createTask(15, "test task");
        when(clock.instant()).thenReturn(now);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        // when
        SUT.start(task.getId());

        // then
        SUT.currentTask().test().assertValue(task);
    }

    @Test
    void timeElapsed_trackerStarted_emitsMillisecondsElapsedWithinAcceptableRange() {
        // given
        Instant start = Instant.now();
        Instant firstTick = start.plusMillis(600);
        Instant secondTick = start.plusMillis(700);
        Task task = createTask(15, "test task");

        when(clock.instant()).thenReturn(start, firstTick, secondTick, start.plusMillis(800));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        // when
        SUT.start(task.getId());
        TestObserver<Long> elapsedTime = SUT.timeElapsed(Observable.fromArray(0L, 100L, 200L)).test();

        // then
        elapsedTime.assertValues(600L, 700L, 800L);
    }

    @Test
    void isRunning_timerNotStarted_emitsFalse() {
        SUT.isRunning().test().assertValue(false);
    }

    @Test
    void isRunning_timerStarted_emitsTrue() {
        Instant now = Instant.ofEpochMilli(12000000);
        long TASK_ID = 15;
        when(clock.instant()).thenReturn(now);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(createTask(TASK_ID, "test task")));

        SUT.start(TASK_ID);
        SUT.isRunning().test().assertValue(true);
    }

    @Test
    void isRunning_timerStopped_emitsFalse() {
        Instant now = Instant.ofEpochMilli(12000000);
        long TASK_ID = 15;
        when(clock.instant()).thenReturn(now);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(createTask(TASK_ID, "test task")));

        SUT.start(TASK_ID);
        SUT.stop();
        SUT.isRunning().test().assertValue(false);
    }
}