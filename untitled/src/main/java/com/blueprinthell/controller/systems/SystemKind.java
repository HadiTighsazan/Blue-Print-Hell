package com.blueprinthell.controller.systems;

import com.blueprinthell.model.PacketModel;
import com.blueprinthell.model.SystemBoxModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/* ================================================================ */
/*                          1) SystemKind                           */
/* ================================================================ */

/** Kind (role) of a SystemBox in the network. */
public enum SystemKind {
    NORMAL,
    SPY,
    MALICIOUS,
    VPN,
    ANTI_TROJAN,
    DISTRIBUTOR,
    MERGER
}