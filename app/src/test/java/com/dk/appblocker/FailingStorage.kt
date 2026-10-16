package com.dk.appblocker

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Models the real failure contract: memory may change even though disk commit returns false. */
class FailingStorage(base: Context, private val owner: LifecycleOwner? = null) : ContextWrapper(base), LifecycleOwner {
    var failWrites = false
    var commits = 0
    override val lifecycle: Lifecycle get() = owner!!.lifecycle
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val delegate = super.getSharedPreferences(name, mode)
        return object : SharedPreferences by delegate {
            override fun edit(): SharedPreferences.Editor {
                val editor = delegate.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean {
                        commits++
                        if (!failWrites) return editor.commit()
                        editor.apply()
                        return false
                    }
                }
            }
        }
    }
}
