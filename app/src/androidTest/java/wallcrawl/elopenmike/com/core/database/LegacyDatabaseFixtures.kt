package wallcrawl.elopenmike.com.core.database

import android.database.sqlite.SQLiteDatabase

internal object LegacyDatabaseFixtures {
    fun createSchema(db: SQLiteDatabase, version: Int) {
        require(version in 1..7)
        db.execSQL("PRAGMA foreign_keys=ON")
        createUserProfiles(db, version)
        createWorkoutSessions(db, version)
        if (version < 4) {
            createLegacyWorkoutChildren(db)
        } else {
            createCurrentWorkoutChildren(db)
            createTemplateTables(db)
        }
    }

    fun insertProfile(db: SQLiteDatabase, version: Int, onboarded: Boolean = true) {
        val columns = mutableListOf(
            "id",
            "name",
            "primaryGoal",
            "experienceLevel",
            "preferredDurationMinutes",
            "daysPerWeek",
            "availableEquipmentJson",
            "preferredUnit",
            "musclePrioritiesJson",
            "excludedExerciseIdsJson"
        )
        val values = mutableListOf<Any>(
            "default_user",
            "Migration Crawler",
            "STRENGTH",
            "ADVANCED",
            75,
            5,
            "Bodyweight|||Dumbbell",
            "KG",
            "Chest:HIGH|||Back:LOW",
            "burpee"
        )
        if (version >= 3) {
            columns += "revision"
            values += 17L
        }
        if (version >= 5) {
            columns += listOf(
                "onboardingCompleted",
                "trainingConstraintsJson",
                "returningAfterBreakWeeks",
                "confirmedStartingLoadsJson"
            )
            values.addAll(
                listOf(
                if (onboarded) 1 else 0,
                "KNEE_SENSITIVE|||LOW_IMPACT_ONLY",
                12,
                "goblet-squat:24.0"
                )
            )
        }
        if (version >= 6) {
            columns += "fitnessGoalsJson"
            values += "STRENGTH|||BUILD_MUSCLE"
        }
        if (version >= 7) {
            columns += "themePreference"
            values += "DARK"
        }

        val placeholders = List(columns.size) { "?" }.joinToString(",")
        db.execSQL(
            "INSERT INTO user_profiles (${columns.joinToString(",")}) VALUES ($placeholders)",
            values.toTypedArray()
        )
    }

    fun insertVersion7HistoryAndTemplate(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO workout_sessions " +
                "(id,name,startedAtTimestamp,completedAtTimestamp,targetDurationMinutes," +
                "actualDurationMinutes,weightUnit,status,origin,sourceTemplateId," +
                "focusMusclesJson,notes) VALUES " +
                "('session-7','Preserved Session',100,200,45,43,'KG','COMPLETED'," +
                "'CUSTOM_TEMPLATE','template-7','Chest','session note')"
        )
        db.execSQL(
            "INSERT INTO workout_exercises " +
                "(id,sessionId,exerciseId,orderIndex,exerciseType,targetSets,targetRepMin," +
                "targetRepMax,targetWeight,targetAssistanceWeight,targetDurationSeconds," +
                "targetDistanceMeters,restSeconds,notes) VALUES " +
                "('workout-exercise-7','session-7','goblet-squat',0,'WEIGHT_REPS',1,8,10," +
                "24.0,NULL,NULL,NULL,90,'exercise note')"
        )
        db.execSQL(
            "INSERT INTO workout_sets " +
                "(id,workoutExerciseId,setNumber,exerciseType,targetReps,completedReps," +
                "targetWeight,completedWeight,targetAssistanceWeight,completedAssistanceWeight," +
                "targetDurationSeconds,completedDurationSeconds,targetDistanceMeters," +
                "completedDistanceMeters,isCompleted,rpe,rir,type) VALUES " +
                "('set-7','workout-exercise-7',1,'WEIGHT_REPS',10,9,24.0,24.0,NULL,NULL," +
                "NULL,NULL,NULL,NULL,1,8.0,2,'NORMAL')"
        )
        db.execSQL(
            "INSERT INTO workout_templates " +
                "(id,name,notes,createdAtTimestamp,updatedAtTimestamp) VALUES " +
                "('template-7','Preserved Template','template note',50,60)"
        )
        db.execSQL(
            "INSERT INTO workout_template_exercises " +
                "(templateId,orderIndex,exerciseId,exerciseType,targetSets,targetRepMin," +
                "targetRepMax,targetWeight,targetAssistanceWeight,targetDurationSeconds," +
                "targetDistanceMeters,restSeconds,notes) VALUES " +
                "('template-7',0,'goblet-squat','WEIGHT_REPS',1,8,10,24.0,NULL,NULL,NULL,90," +
                "'template exercise note')"
        )
    }

    private fun createUserProfiles(db: SQLiteDatabase, version: Int) {
        val extraColumns = buildString {
            if (version >= 3) append(", revision INTEGER NOT NULL DEFAULT 0")
            if (version >= 5) {
                append(", onboardingCompleted INTEGER NOT NULL DEFAULT 0")
                append(", trainingConstraintsJson TEXT NOT NULL DEFAULT ''")
                append(", returningAfterBreakWeeks INTEGER NOT NULL DEFAULT 0")
                append(", confirmedStartingLoadsJson TEXT NOT NULL DEFAULT ''")
            }
            if (version >= 6) append(", fitnessGoalsJson TEXT NOT NULL DEFAULT ''")
            if (version >= 7) append(", themePreference TEXT NOT NULL DEFAULT 'SYSTEM'")
        }
        db.execSQL(
            "CREATE TABLE user_profiles (" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, primaryGoal TEXT NOT NULL, " +
                "experienceLevel TEXT NOT NULL, preferredDurationMinutes INTEGER NOT NULL, " +
                "daysPerWeek INTEGER NOT NULL, availableEquipmentJson TEXT NOT NULL, " +
                "preferredUnit TEXT NOT NULL, musclePrioritiesJson TEXT NOT NULL, " +
                "excludedExerciseIdsJson TEXT NOT NULL$extraColumns)"
        )
    }

    private fun createWorkoutSessions(db: SQLiteDatabase, version: Int) {
        val weightUnit = if (version >= 2) ", weightUnit TEXT NOT NULL DEFAULT 'LBS'" else ""
        val provenance = if (version >= 4) {
            ", origin TEXT NOT NULL DEFAULT 'PLANNER', sourceTemplateId TEXT"
        } else {
            ""
        }
        db.execSQL(
            "CREATE TABLE workout_sessions (" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "startedAtTimestamp INTEGER NOT NULL, completedAtTimestamp INTEGER, " +
                "targetDurationMinutes INTEGER NOT NULL, actualDurationMinutes INTEGER NOT NULL" +
                "$weightUnit, status TEXT NOT NULL$provenance, " +
                "focusMusclesJson TEXT NOT NULL, notes TEXT NOT NULL)"
        )
        if (version >= 2) {
            db.execSQL(
                "CREATE INDEX index_workout_sessions_status_completedAtTimestamp " +
                    "ON workout_sessions(status, completedAtTimestamp)"
            )
        }
    }

    private fun createLegacyWorkoutChildren(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE workout_exercises (id TEXT NOT NULL PRIMARY KEY, " +
                "sessionId TEXT NOT NULL, exerciseId TEXT NOT NULL, orderIndex INTEGER NOT NULL, " +
                "targetSets INTEGER NOT NULL, targetRepMin INTEGER NOT NULL, " +
                "targetRepMax INTEGER NOT NULL, targetWeight REAL, notes TEXT NOT NULL, " +
                "FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE workout_sets (id TEXT NOT NULL PRIMARY KEY, " +
                "workoutExerciseId TEXT NOT NULL, setNumber INTEGER NOT NULL, " +
                "targetReps INTEGER NOT NULL, completedReps INTEGER, targetWeight REAL, " +
                "completedWeight REAL, isCompleted INTEGER NOT NULL, rpe REAL, rir INTEGER, " +
                "type TEXT NOT NULL, FOREIGN KEY(workoutExerciseId) " +
                "REFERENCES workout_exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        createWorkoutChildIndices(db)
    }

    private fun createCurrentWorkoutChildren(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE workout_exercises (id TEXT NOT NULL PRIMARY KEY, " +
                "sessionId TEXT NOT NULL, exerciseId TEXT NOT NULL, orderIndex INTEGER NOT NULL, " +
                "exerciseType TEXT NOT NULL, targetSets INTEGER NOT NULL, targetRepMin INTEGER, " +
                "targetRepMax INTEGER, targetWeight REAL, targetAssistanceWeight REAL, " +
                "targetDurationSeconds INTEGER, targetDistanceMeters REAL, " +
                "restSeconds INTEGER NOT NULL, notes TEXT NOT NULL, " +
                "FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE workout_sets (id TEXT NOT NULL PRIMARY KEY, " +
                "workoutExerciseId TEXT NOT NULL, setNumber INTEGER NOT NULL, " +
                "exerciseType TEXT NOT NULL, targetReps INTEGER, completedReps INTEGER, " +
                "targetWeight REAL, completedWeight REAL, targetAssistanceWeight REAL, " +
                "completedAssistanceWeight REAL, targetDurationSeconds INTEGER, " +
                "completedDurationSeconds INTEGER, targetDistanceMeters REAL, " +
                "completedDistanceMeters REAL, isCompleted INTEGER NOT NULL, rpe REAL, " +
                "rir INTEGER, type TEXT NOT NULL, FOREIGN KEY(workoutExerciseId) " +
                "REFERENCES workout_exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        createWorkoutChildIndices(db)
    }

    private fun createWorkoutChildIndices(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX index_workout_exercises_sessionId ON workout_exercises(sessionId)")
        db.execSQL(
            "CREATE INDEX index_workout_sets_workoutExerciseId " +
                "ON workout_sets(workoutExerciseId)"
        )
    }

    private fun createTemplateTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE workout_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "notes TEXT NOT NULL, createdAtTimestamp INTEGER NOT NULL, " +
                "updatedAtTimestamp INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE workout_template_exercises (templateId TEXT NOT NULL, " +
                "orderIndex INTEGER NOT NULL, exerciseId TEXT NOT NULL, exerciseType TEXT NOT NULL, " +
                "targetSets INTEGER NOT NULL, targetRepMin INTEGER, targetRepMax INTEGER, " +
                "targetWeight REAL, targetAssistanceWeight REAL, targetDurationSeconds INTEGER, " +
                "targetDistanceMeters REAL, restSeconds INTEGER NOT NULL, notes TEXT NOT NULL, " +
                "PRIMARY KEY(templateId, orderIndex), FOREIGN KEY(templateId) " +
                "REFERENCES workout_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX index_workout_template_exercises_templateId " +
                "ON workout_template_exercises(templateId)"
        )
    }
}
