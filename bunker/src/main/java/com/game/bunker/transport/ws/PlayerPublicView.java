package com.game.bunker.transport.ws;

import com.game.bunker.characteristic.entity.Survivor;
import com.game.bunker.user.entity.User;

public record PlayerPublicView(
        User user,
        Survivor survivor
) {
}
