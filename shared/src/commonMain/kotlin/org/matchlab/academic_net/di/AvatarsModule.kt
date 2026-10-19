package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.avatars.AvatarDirectory
import org.matchlab.academic_net.gateway.GatewaySession

/** The uploaded pictures of people, for the avatars of every screen of the session. */
internal interface AvatarsModule {
    @Provides
    @SessionScope
    fun avatarDirectory(gateway: GatewaySession): AvatarDirectory = AvatarDirectory(gateway.users, gateway.media)
}
