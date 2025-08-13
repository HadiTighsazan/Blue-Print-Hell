package com.blueprinthell.controller.core;

import com.blueprinthell.controller.NetworkController;
import com.blueprinthell.snapshot.NetworkSnapshot;
import com.blueprinthell.controller.SnapshotManager;
import com.blueprinthell.controller.SnapshotService;

public class SnapshotCoreController implements NetworkController {
    public final SnapshotManager snapshotMgr = new SnapshotManager();
    public SnapshotService snapshotSvc;

    public SnapshotCoreController() {
    }

    public SnapshotManager getSnapshotMgr() {
        return snapshotMgr;
    }

    public SnapshotService getSnapshotSvc() {
        return snapshotSvc;
    }
    @Override
    public NetworkSnapshot captureSnapshot() {
        return snapshotSvc.buildSnapshot();
    }

    @Override
    public void restoreState(NetworkSnapshot snap) {
        snapshotSvc.restore(snap);
    }

}