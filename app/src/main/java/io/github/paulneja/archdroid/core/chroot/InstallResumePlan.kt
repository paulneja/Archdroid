package io.github.paulneja.archdroid.core.chroot

object InstallResumePlan {
    private val ordered = listOf(
        ChrootInstaller.Step.DOWNLOAD,
        ChrootInstaller.Step.VERIFY,
        ChrootInstaller.Step.EXTRACT,
        ChrootInstaller.Step.BASE_CONFIG,
        ChrootInstaller.Step.USER,
        ChrootInstaller.Step.PACKAGES,
        ChrootInstaller.Step.CLEANUP,
    )

    fun stepsFrom(step: ChrootInstaller.Step): List<ChrootInstaller.Step> =
        ordered.dropWhile { it != step }

    fun next(step: ChrootInstaller.Step): ChrootInstaller.Step? =
        ordered.getOrNull(ordered.indexOf(step) + 1)
}
