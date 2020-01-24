package org.videolan.vlc.gui.helpers

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

interface IEventsSource<E> {
    val eventsChannel : Channel<E>
    val events : Flow<E>
}

class EventsSource<E> : IEventsSource<E> {
    override val eventsChannel = Channel<E>(Channel.CONFLATED)
    override val events = eventsChannel.consumeAsFlow()
}

sealed class Click(val position: Int)
class SimpleClick(position: Int) : Click(position)
class LongClick(position: Int) : Click(position)
class CtxClick(position: Int) : Click(position)
class ImageClick(position: Int) : Click(position)