package com.blueprinthell.controller.persistence;

import com.blueprinthell.controller.simulation.NetworkController;
import com.blueprinthell.model.Updatable;
import com.blueprinthell.snapshot.NetworkSnapshot;

public class SnapshotController implements Updatable {
    private final SnapshotManager snapshotManager;
   private final NetworkController networkController;
    private double elapsedTime = 0;

            public SnapshotController(NetworkController controller, SnapshotManager snapshotManager) {
                this.networkController = controller;
                this.snapshotManager = snapshotManager;
            }

            @Override
    public void update(double dt) {
                elapsedTime = dt;
                // ضبط فریم با اسنپ‌شات «جدید» از خود NetworkController
                        NetworkSnapshot snap = networkController.captureSnapshot();
                snapshotManager.recordSnapshot(snap);
            }
}