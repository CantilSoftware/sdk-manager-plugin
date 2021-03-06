package com.jakewharton.sdkmanager
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.jakewharton.sdkmanager.internal.PackageResolver
import com.jakewharton.sdkmanager.internal.SdkResolver
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.StopExecutionException

import java.util.concurrent.TimeUnit

class SdkManagerPlugin implements Plugin<Project> {
  final Logger log = Logging.getLogger SdkManagerPlugin

  @Override void apply(Project project) {
    if (hasAndroidPlugin(project)) {
      throw new StopExecutionException(
          "Must be applied before 'android' or 'android-library' plugin.")
    }

    if (isOfflineBuild(project)) {
      log.debug 'Offline build. Skipping package resolution.'
      return
    }

    project.extensions.create("sdkManager", SdkManagerExtension)

    // Eager resolve the SDK and local.properties pointer.
    def sdk
    time "SDK resolve", {
      sdk = SdkResolver.resolve project
    }

    // Defer resolving SDK package dependencies until after the model is finalized.
    project.afterEvaluate {
      if (!hasAndroidPlugin(project)) {
        log.debug 'No Android plugin, attempting emulator.'
        time "Package resolve emulator", {
          PackageResolver.resolveEmulator project, sdk
        }
      } else {
        time "Package resolve", {
          PackageResolver.resolve project, sdk
        }
      }
    }
  }

  def time(String name, Closure task) {
    long before = java.lang.System.nanoTime()
    task.run()
    long after = java.lang.System.nanoTime()
    long took = TimeUnit.NANOSECONDS.toMillis(after - before)
    log.info "$name took $took ms."
  }

  static def hasAndroidPlugin(Project project) {
    return project.plugins.hasPlugin(AppPlugin) || project.plugins.hasPlugin(LibraryPlugin)
  }

  static def isOfflineBuild(Project project) {
    return project.getGradle().getStartParameter().isOffline()
  }
}
