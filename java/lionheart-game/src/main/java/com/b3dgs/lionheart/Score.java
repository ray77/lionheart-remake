/*
 * Copyright (C) 2013-2024 Byron 3D Games Studio (www.b3dgs.com) Pierre-Alexandre (contact@b3dgs.com)
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not,
 * see <a href="https://www.gnu.org/licenses/">https://www.gnu.org/licenses/</a>.
 */
package com.b3dgs.lionheart;

import com.b3dgs.lionengine.LionEngineException;

/**
 * Score of the current run.
 * <p>
 * The hud works out a score from how far the player got, the talismans and the lives left, but it is
 * built anew for every stage and would start counting from zero each time. A run spans fourteen of
 * them, so what a stage was worth is banked when the next one loads and the total is what counts.
 * <p>
 * Kept apart from the hud because it outlives it, and because things that end a run - the game over,
 * a leaderboard elsewhere - want the total without knowing anything about drawing.
 */
public final class Score
{
    /** Listener notified when a run ends, with the final score. */
    public interface EndListener
    {
        /**
         * Called once when a run has ended.
         *
         * @param score The score reached.
         */
        void notifyRunEnded(int score);
    }

    /** Score of the stages already finished. */
    private static int banked;
    /** Score of the stage being played. */
    private static int current;
    /** Whether the end of this run has already been announced. */
    private static boolean reported;
    /** Notified once a run ends, never <code>null</code>. */
    private static EndListener listener = score ->
    {
        // Nothing by default, a run that ends is not an event on the desktop.
    };

    /**
     * Start over. To be called when a new run begins.
     */
    public static void reset()
    {
        banked = 0;
        current = 0;
        reported = false;
    }

    /**
     * Set what the stage being played is worth so far.
     *
     * @param score The score of the current stage.
     */
    public static void setCurrent(int score)
    {
        current = score;
    }

    /**
     * Put the current stage away and start counting the next one. To be called when a stage ends.
     */
    public static void bank()
    {
        banked += current;
        current = 0;
    }

    /**
     * Get the score of the whole run.
     *
     * @return The stages finished plus the one being played.
     */
    public static int get()
    {
        return banked + current;
    }

    /**
     * Set who to tell when a run ends.
     *
     * @param endListener The listener, <code>null</code> to stop being told.
     */
    public static void setEndListener(EndListener endListener)
    {
        if (endListener == null)
        {
            listener = score ->
            {
                // Nothing.
            };
        }
        else
        {
            listener = endListener;
        }
    }

    /**
     * Tell the listener that the run has ended, handing it the total.
     * <p>
     * A run can reach its end by more than one road - the last credit spent, or the player turning
     * the continue screen down - so this only ever fires once, until the next {@link #reset()}.
     */
    public static void notifyRunEnded()
    {
        if (reported)
        {
            return;
        }
        reported = true;
        listener.notifyRunEnded(get());
    }

    /**
     * Private constructor.
     */
    private Score()
    {
        throw new LionEngineException(LionEngineException.ERROR_PRIVATE_CONSTRUCTOR);
    }
}
