package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InstallResumePlanTest {

    @Test
    fun `resume after base configuration never repeats extraction`() {
        val steps = InstallResumePlan.stepsFrom(ChrootInstaller.Step.BASE_CONFIG)

        assertFalse(steps.contains(ChrootInstaller.Step.EXTRACT))
        assertEquals(ChrootInstaller.Step.BASE_CONFIG, steps.first())
    }

    @Test
    fun `resume at user safely repeats user and later phases only`() {
        assertEquals(
            listOf(
                ChrootInstaller.Step.USER,
                ChrootInstaller.Step.PACKAGES,
                ChrootInstaller.Step.CLEANUP,
            ),
            InstallResumePlan.stepsFrom(ChrootInstaller.Step.USER),
        )
    }

    @Test
    fun `resume at packages does not repeat account changes`() {
        assertEquals(
            listOf(ChrootInstaller.Step.PACKAGES, ChrootInstaller.Step.CLEANUP),
            InstallResumePlan.stepsFrom(ChrootInstaller.Step.PACKAGES),
        )
    }
}
