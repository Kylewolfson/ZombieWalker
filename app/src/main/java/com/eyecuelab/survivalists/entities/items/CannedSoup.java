package com.eyecuelab.survivalists.entities.items;

import com.eyecuelab.survivalists.entities.interfaces.Inventory;

/**
 * Created by eyecue on 5/16/16.
 */
public class CannedSoup implements Inventory {
    public boolean equipped;
    public int itemEffect = 5;

    @Override
    public int useItem(int hunger) {
        return hunger + itemEffect;
    }

    @Override
    public String getName() {
        return "Tomato Soup";
    }

    @Override
    public String getDescription() {
        return "Cold soup from the can never tasted so good.";
    }

    @Override
    public void drop() {
        equipped = false;
    }

    @Override
    public void pickup() {
        equipped = true;
    }
}
