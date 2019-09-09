package com.pierbezuhoff.clonium.ui.newgame

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.pierbezuhoff.clonium.utils.AndroidLogOf
import com.pierbezuhoff.clonium.utils.WithLog
import com.pierbezuhoff.clonium.utils.impossibleCaseOf

class TabFragmentAdapter(
    fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT)
    , WithLog by AndroidLogOf<TabFragmentAdapter>()
{
    override fun getCount(): Int =
        1
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> PlayersFragment()
            else -> impossibleCaseOf(position)
        }
    }

    override fun getPageTitle(position: Int): CharSequence? =
        when (position) {
            0 -> "Players"
            else -> impossibleCaseOf(position)
        }
}
