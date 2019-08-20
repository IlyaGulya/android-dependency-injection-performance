package com.sloydev.dependencyinjectionperformance

import android.os.Build
import com.sloydev.dependencyinjectionperformance.custom.DIContainer
import com.sloydev.dependencyinjectionperformance.custom.customJavaModule
import com.sloydev.dependencyinjectionperformance.custom.customKotlinModule
import com.sloydev.dependencyinjectionperformance.dagger2.DaggerJavaDaggerComponent
import com.sloydev.dependencyinjectionperformance.dagger2.DaggerKotlinConstructorInjectionDaggerComponent
import com.sloydev.dependencyinjectionperformance.dagger2.DaggerKotlinDaggerComponent
import com.sloydev.dependencyinjectionperformance.dagger2.JavaDaggerComponent
import com.sloydev.dependencyinjectionperformance.dagger2.KotlinConstructorInjectionDaggerComponent
import com.sloydev.dependencyinjectionperformance.dagger2.KotlinDaggerComponent
import com.sloydev.dependencyinjectionperformance.katana.katanaJavaModule
import com.sloydev.dependencyinjectionperformance.katana.katanaKotlinModule
import com.sloydev.dependencyinjectionperformance.koin.koinConstructorModule
import com.sloydev.dependencyinjectionperformance.koin.koinJavaModule
import com.sloydev.dependencyinjectionperformance.koin.koinKotlinModule
import org.kodein.di.Kodein
import org.kodein.di.direct
import org.kodein.di.erased.instance
import org.koin.core.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.get
import org.rewedigital.katana.Component
import org.rewedigital.katana.Katana
import org.rewedigital.katana.android.environment.AndroidEnvironmentContext
import org.rewedigital.katana.android.environment.AndroidEnvironmentContext.Profile.SPEED
import org.rewedigital.katana.createComponent
import javax.inject.Inject

class InjectionTest : KoinComponent {

    private val kotlinDaggerTest = KotlinDaggerTest()
    private val javaDaggerTest = JavaDaggerTest()
    private val constructorDaggerTest = KotlinConstructorDaggerTest()

    private val rounds = 100

    fun runTests() {
        val results = listOf(
            koinTest(),
            kodeinTest(),
            katanaTest(),
            customTest(),
            daggerTest()
        )
        reportMarkdown(results)
    }

    private fun reportMarkdown(results: List<LibraryResult>) {
        log("Done!")
        log(" ")
        log("${Build.BRAND} ${Build.DEVICE} with Android ${Build.VERSION.RELEASE}")
        log(" ")
        log("Library | Setup Kotlin | Setup Java | Setup Kotlin Constructor | Inject Kotlin | Inject Java | Inject Kotlin Constructor")
        log("--- | ---:| ---:| ---:| ---: | ---: | ---:|")
        results.forEach { result ->
            val setups = Variant.values().map { result.getOrNull(it)?.startupTime }
            val injections = Variant.values().map { result.getOrNull(it)?.injectionTime }
            val all = (setups + injections).map { it?.median()?.format() }
            log("**${result.injectorName}** | ${all.joinToString(" | ")}")
        }
    }

    private fun runTest(
        setup: () -> Unit,
        test: () -> Unit,
        teardown: () -> Unit = {}
    ): TestResult {
        val startup = (1..rounds).map { measureTime { setup() }.also { teardown() } }
        setup()

        val testDurations = (1..rounds).map { measureTime { test() } }
        teardown()
        return TestResult(startup, testDurations)
    }

    private fun koinTest(): LibraryResult {
        log("Running Koin...")
        return LibraryResult("Koin", mapOf(
            Variant.KOTLIN to runTest(
                setup = {
                    startKoin {
                        modules(koinKotlinModule)
                    }
                },
                test = { get<Fib20>() },
                teardown = { stopKoin() }
            ),
            Variant.JAVA to runTest(
                setup = {
                    startKoin {
                        modules(koinJavaModule)
                    }
                },
                test = { get<FibonacciJava.Fib20>() },
                teardown = { stopKoin() }
            ),
            Variant.KOTLIN_CONSTRUCTOR to runTest(
                setup = {
                    startKoin {
                        modules(koinConstructorModule)
                    }
                },
                test = { get<Fib20>() },
                teardown = { stopKoin() }
            )
        ))
    }

    private fun kodeinTest(): LibraryResult {
        log("Running Kodein...")
        lateinit var kodein: Kodein
        return LibraryResult("Kodein", mapOf(
            Variant.KOTLIN to runTest(
                setup = { kodein = Kodein { import(kodeinKotlinModule) } },
                test = { kodein.direct.instance<Fib20>() }
            ),
            Variant.JAVA to runTest(
                setup = { kodein = Kodein { import(kodeinKotlinModule) } },
                test = { kodein.direct.instance<Fib20>() }
            )
        ))
    }

    private fun katanaTest(): LibraryResult {
        log("Running Katana...")
        Katana.environmentContext = AndroidEnvironmentContext(profile = SPEED)
        lateinit var component: Component
        return LibraryResult("Katana", mapOf(
            Variant.KOTLIN to runTest(
                setup = { component = createComponent(modules = listOf(katanaKotlinModule)) },
                test = { component.injectNow<Fib20>() }
            ),
            Variant.JAVA to runTest(
                setup = { component = createComponent(modules = listOf(katanaJavaModule)) },
                test = { component.injectNow<FibonacciJava.Fib20>() }
            )
        ))
    }

    private fun customTest(): LibraryResult {
        log("Running Custom...")
        return LibraryResult("Custom", mapOf(
            Variant.KOTLIN to runTest(
                setup = { DIContainer.loadModule(customKotlinModule) },
                test = { DIContainer.get<Fib20>() },
                teardown = { DIContainer.unloadModules() }
            ),
            Variant.JAVA to runTest(
                setup = { DIContainer.loadModule(customJavaModule) },
                test = { DIContainer.get<FibonacciJava.Fib20>() },
                teardown = { DIContainer.unloadModules() }
            )
        ))
    }

    private fun daggerTest(): LibraryResult {
        log("Running Dagger...")
        lateinit var kotlinComponent: KotlinDaggerComponent
        lateinit var javaComponent: JavaDaggerComponent
        lateinit var constructorComponent: KotlinConstructorInjectionDaggerComponent
        return LibraryResult("Dagger", mapOf(
            Variant.KOTLIN to runTest(
                setup = { kotlinComponent = DaggerKotlinDaggerComponent.create() },
                test = { kotlinComponent.inject(kotlinDaggerTest) }
            ),
            Variant.JAVA to runTest(
                setup = { javaComponent = DaggerJavaDaggerComponent.create() },
                test = { javaComponent.inject(javaDaggerTest) }
            ),
            Variant.KOTLIN_CONSTRUCTOR to runTest(
                setup = { constructorComponent = DaggerKotlinConstructorInjectionDaggerComponent.create() },
                test = { constructorComponent.inject(constructorDaggerTest)}
            )
        ))
    }

    class KotlinDaggerTest {
        @Inject
        lateinit var daggerFib20: Fib20
    }

    class KotlinConstructorDaggerTest {
        @Inject
        lateinit var daggerFib20: Fib20
    }

    class JavaDaggerTest {
        @Inject
        lateinit var daggerFib20: FibonacciJava.Fib20
    }
}
