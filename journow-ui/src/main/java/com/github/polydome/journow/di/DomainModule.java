package com.github.polydome.journow.di;

import com.github.polydome.journow.data.Database;
import com.github.polydome.journow.data.preferences.PreferencesTrackerDataStorage;
import com.github.polydome.journow.data.repository.ProjectRepositoryImpl;
import com.github.polydome.journow.data.repository.SessionRepositoryImpl;
import com.github.polydome.journow.data.repository.TaskRepositoryImpl;
import com.github.polydome.journow.data.event.DataEventBus;
import com.github.polydome.journow.domain.model.TrackerData;
import com.github.polydome.journow.domain.repository.ProjectRepository;
import com.github.polydome.journow.domain.repository.SessionRepository;
import com.github.polydome.journow.domain.repository.TaskRepository;
import com.github.polydome.journow.domain.service.TrackerDataStorage;
import com.github.polydome.journow.domain.usecase.LogSessionUseCase;
import dagger.Module;
import dagger.Provides;

import java.time.Clock;
import java.util.Optional;

@Module
public class DomainModule {
    @Provides
    TaskRepository taskRepository(Database database, DataEventBus dataEventBus) {
        return new TaskRepositoryImpl(database, dataEventBus);
    }

    @Provides
    SessionRepository sessionRepository(Database database, DataEventBus dataEventBus) {
        return new SessionRepositoryImpl(database, dataEventBus);
    }

    @Provides
    ProjectRepository projectRepository(Database database, DataEventBus dataEventBus) {
        return new ProjectRepositoryImpl(database, dataEventBus);
    }

    @Provides
    LogSessionUseCase logSessionUseCase(TaskRepository taskRepository, SessionRepository sessionRepository) {
        return new LogSessionUseCase(taskRepository, sessionRepository);
    }

    @Provides
    TrackerDataStorage trackerDataStorage() {
        return new PreferencesTrackerDataStorage();
    }

    @Provides
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
