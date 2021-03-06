package com.github.polydome.journow.data.repository;

import com.github.polydome.journow.data.Database;
import com.github.polydome.journow.data.event.DataEvent;
import com.github.polydome.journow.data.event.DataEventBus;
import com.github.polydome.journow.domain.exception.NoSuchTaskException;
import com.github.polydome.journow.domain.model.Project;
import com.github.polydome.journow.domain.repository.ProjectRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.polydome.journow.data.repository.ResultSetUtil.parseProject;

public class ProjectRepositoryImpl implements ProjectRepository {
    private final Database database;
    private final DataEventBus dataEventBus;

    private PreparedStatement selectAll;
    private PreparedStatement insertWithId;
    private PreparedStatement insertNew;
    private PreparedStatement findById;
    private PreparedStatement update;
    private PreparedStatement findTrackedTime;
    private PreparedStatement findOne;

    public ProjectRepositoryImpl(Database database, DataEventBus dataEventBus) {
        this.database = database;
        this.dataEventBus = dataEventBus;
    }

    @Override
    public List<Project> findAll() {
        if (!database.isReady())
            throw new IllegalStateException("Database is not ready");

        ArrayList<Project> projects = new ArrayList<>();

        try {
            if (selectAll == null)
                selectAll = database.getConnection().prepareStatement("select * from project");

            try (ResultSet rows = selectAll.executeQuery()) {
                while (rows.next()) {
                    projects.add(parseProject(rows));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return projects;
    }

    @Override
    public Project insert(Project project) {
        if (!database.isReady())
            throw new IllegalStateException("Database is not ready");

        try {
            if (project.getId() > 0) {
                if (insertWithId == null)
                    insertWithId = getConnection().prepareStatement("insert into project (project_id, project_name) values (?, ?)");

                insertWithId.setLong(1, project.getId());
                insertWithId.setString(2, project.getName());
                insertWithId.execute();

                Optional<Project> insertedProject = findById(project.getId());
                if (insertedProject.isPresent()) {
                    dataEventBus.pushProjectEvent(DataEvent.insertOne(insertedProject.get().getId()));
                    return insertedProject.get();
                }
            } else {
                if (insertNew == null)
                    insertNew = getConnection().prepareStatement("insert into project (project_name) values (?)");

                insertNew.setString(1, project.getName());
                insertNew.execute();

                try (ResultSet generatedKeys = insertNew.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        Optional<Project> insertedProject = findById(id);
                        if (insertedProject.isPresent()) {
                            dataEventBus.pushProjectEvent(DataEvent.insertOne(id));
                            return insertedProject.get();
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private Optional<Project> findById(long id) {
        if (!database.isReady())
            throw new IllegalStateException("Database is not ready");

        try {
            if (findById == null)
                findById = getConnection().prepareStatement("select * from project where project_id = ?");

            findById.setLong(1, id);

            try (var rs = findById.executeQuery()) {
                if (rs.next()) {
                    Project project = parseProject(rs);
                    if (project != null)
                        return Optional.of(project);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    @Override
    public void update(Project project) {
        if (!database.isReady())
            throw new IllegalStateException("Database is not ready");

        if (update == null) {
            try {
                update = getConnection().prepareStatement("update project set project_name = ? where project_id = ?");

                update.setString(1, project.getName());
                update.setLong(2, project.getId());

                if (update.executeUpdate() > 0)
                    dataEventBus.pushProjectEvent(DataEvent.updateOne(project.getId()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public long findTotalTrackedMillis(long projectId) {
        if (!database.isReady())
            throw new IllegalStateException("Database is not ready");

        try {
            if (!projectExists(projectId))
                throw new NoSuchTaskException(projectId);

            if (findTrackedTime == null)
                findTrackedTime = getConnection().prepareStatement("select sum(end_date - start_date)\n" +
                        "from session\n" +
                        "         inner join task t on session.task_id = t.task_id\n" +
                        "         " +
                        "     inner join project p on t.project_id = p.project_id\n" +
                        "where p.project_id = ?");

            findTrackedTime.setLong(1, projectId);

            try (var rs = findTrackedTime.executeQuery()) {
                if (rs.next())
                    return rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    private boolean projectExists(long projectId) throws SQLException {
        if (findOne == null)
            findOne = getConnection().prepareStatement("select * from project where project_id = ?");

        findOne.setLong(1, projectId);

        try (var rs = findOne.executeQuery()) {
            return rs.next();
        }
    }

    private Connection getConnection() throws SQLException {
        return database.getConnection();
    }
}
