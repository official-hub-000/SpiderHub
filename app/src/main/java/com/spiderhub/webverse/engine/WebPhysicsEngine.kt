package com.spiderhub.webverse.engine

import com.spiderhub.webverse.game.CityBuilding
import com.spiderhub.webverse.game.SwingAnchor
import com.spiderhub.webverse.game.Vec3
import org.jbox2d.callbacks.ContactFilter
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Fixture
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D Spider-Man Web-Swinging Physics Engine powered by JBox2D (org.jbox2d).
 *
 * Uses two coupled JBox2D physics worlds:
 * 1. xzWorld: Manages horizontal velocity, linear damping, world bounds, building AABB collision
 *    detection (height-filtered via ContactFilter), and wall-run surface detection (ContactListener).
 * 2. yWorld: Manages gravity, vertical velocity, ground/rooftop contacts, and pendulum web-rope
 *    centripetal impulse exchange with xzWorld.
 */
class WebPhysicsEngine {

    var gravity = -26.5f
        set(value) {
            field = value
            yWorld.gravity = Vec2(0f, value)
        }

    var maxHorizontalSpeed = 56f
    var swingPumpForce = 20f

    private val xzWorld = World(Vec2(0f, 0f))
    private val yWorld = World(Vec2(0f, gravity))

    private lateinit var playerXzBody: Body
    private lateinit var playerYBody: Body
    private lateinit var floorSupportBody: Body

    private var currentPlayerY = 0f
    private var wallContactCount = 0
    private var floorContactCount = 0

    data class StepResult(
        val landedHard: Boolean,
        val speed: Float
    )

    fun initWorld(buildings: List<CityBuilding>, startPos: Vec3) {
        currentPlayerY = startPos.y
        wallContactCount = 0
        floorContactCount = 0

        // Height-aware ContactFilter so player glides cleanly over rooftops while swinging high
        xzWorld.setContactFilter(object : ContactFilter() {
            override fun shouldCollide(fixtureA: Fixture, fixtureB: Fixture): Boolean {
                val hA = fixtureA.body.userData as? Float
                val hB = fixtureB.body.userData as? Float
                val bHeight = hA ?: hB ?: return true
                return currentPlayerY < bHeight - 1.25f
            }
        })

        xzWorld.setContactListener(object : ContactListener {
            override fun beginContact(contact: org.jbox2d.dynamics.contacts.Contact) {
                if (contact.fixtureA.body === playerXzBody || contact.fixtureB.body === playerXzBody) {
                    val other = if (contact.fixtureA.body === playerXzBody) contact.fixtureB.body else contact.fixtureA.body
                    if (other.userData is Float) wallContactCount++
                }
            }

            override fun endContact(contact: org.jbox2d.dynamics.contacts.Contact) {
                if (contact.fixtureA.body === playerXzBody || contact.fixtureB.body === playerXzBody) {
                    val other = if (contact.fixtureA.body === playerXzBody) contact.fixtureB.body else contact.fixtureA.body
                    if (other.userData is Float) wallContactCount = max(0, wallContactCount - 1)
                }
            }

            override fun preSolve(contact: org.jbox2d.dynamics.contacts.Contact, oldManifold: Manifold) {}
            override fun postSolve(contact: org.jbox2d.dynamics.contacts.Contact, impulse: ContactImpulse) {}
        })

        // Register all skyscraper AABBs as static JBox2D Box bodies
        for (b in buildings) {
            val bd = BodyDef().apply {
                type = BodyType.STATIC
                position.set(b.x, b.z)
                userData = b.height
            }
            val body = xzWorld.createBody(bd)
            val box = PolygonShape().apply {
                setAsBox(b.width * 0.5f, b.depth * 0.5f)
            }
            body.createFixture(FixtureDef().apply {
                shape = box
                friction = 0.0f
                restitution = 0.0f
            })
        }

        // World boundary walls in xzWorld
        createStaticBox(xzWorld, 0f, -168f, 160f, 3f)
        createStaticBox(xzWorld, 0f, 138f, 160f, 3f)
        createStaticBox(xzWorld, -148f, 0f, 3f, 165f)
        createStaticBox(xzWorld, 148f, 0f, 3f, 165f)

        // Dynamic player horizontal body
        val pxzDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(startPos.x, startPos.z)
            fixedRotation = true
            bullet = true
            linearDamping = 0.12f
        }
        playerXzBody = xzWorld.createBody(pxzDef)
        playerXzBody.createFixture(FixtureDef().apply {
            shape = CircleShape().apply { radius = 0.8f }
            density = 1.0f
            friction = 0.0f
            restitution = 0.0f
        })

        // Dynamic player vertical body in yWorld (gravity + rooftop/street floor)
        yWorld.setContactListener(object : ContactListener {
            override fun beginContact(contact: org.jbox2d.dynamics.contacts.Contact) {
                floorContactCount++
            }

            override fun endContact(contact: org.jbox2d.dynamics.contacts.Contact) {
                floorContactCount = max(0, floorContactCount - 1)
            }

            override fun preSolve(contact: org.jbox2d.dynamics.contacts.Contact, oldManifold: Manifold) {}
            override fun postSolve(contact: org.jbox2d.dynamics.contacts.Contact, impulse: ContactImpulse) {}
        })

        floorSupportBody = createStaticBox(yWorld, 0f, -1.0f, 200f, 1.0f)

        val pyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(0f, startPos.y + 0.5f)
            fixedRotation = true
            bullet = true
        }
        playerYBody = yWorld.createBody(pyDef)
        playerYBody.createFixture(FixtureDef().apply {
            shape = CircleShape().apply { radius = 0.5f }
            density = 1.0f
            friction = 0.0f
            restitution = 0.0f
        })
    }

    private fun createStaticBox(world: World, x: Float, y: Float, halfW: Float, halfH: Float): Body {
        val bd = BodyDef().apply {
            type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bd)
        body.createFixture(FixtureDef().apply {
            shape = PolygonShape().apply { setAsBox(halfW, halfH) }
            friction = 0.0f
            restitution = 0.0f
        })
        return body
    }

    fun teleportPlayer(pos: Vec3, vel: Vec3) {
        currentPlayerY = pos.y
        playerXzBody.setTransform(Vec2(pos.x, pos.z), 0f)
        playerXzBody.linearVelocity = Vec2(vel.x, vel.z)
        playerYBody.setTransform(Vec2(0f, pos.y + 0.5f), 0f)
        playerYBody.linearVelocity = Vec2(0f, vel.y)
    }

    fun findBestSwingAnchor(
        playerPos: Vec3,
        cameraYaw: Float,
        anchors: List<SwingAnchor>
    ): SwingAnchor? {
        val fwdX = -sin(cameraYaw)
        val fwdZ = -cos(cameraYaw)
        var bestAnchor: SwingAnchor? = null
        var bestScore = -Float.MAX_VALUE

        for (anchor in anchors) {
            val dx = anchor.x - playerPos.x
            val dy = anchor.y - playerPos.y
            val dz = anchor.z - playerPos.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 6f || dist > 72f || dy < 2.0f) continue

            val forwardDot = (dx * fwdX + dz * fwdZ) / dist
            if (forwardDot < -0.25f) continue

            val score = forwardDot * 2.6f - dist * 0.02f + dy * 0.045f
            if (score > bestScore) {
                bestScore = score
                bestAnchor = anchor
            }
        }
        return bestAnchor
    }

    fun startSwing(
        anchor: SwingAnchor,
        playerPos: Vec3,
        playerVel: Vec3,
        cameraYaw: Float,
        swingTarget: Vec3
    ): Float {
        swingTarget.x = anchor.x
        swingTarget.y = anchor.y
        swingTarget.z = anchor.z
        val dx = swingTarget.x - playerPos.x
        val dy = swingTarget.y - playerPos.y
        val dz = swingTarget.z - playerPos.z
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceIn(10f, 52f)

        val fwdX = -sin(cameraYaw)
        val fwdZ = -cos(cameraYaw)
        val diveBoost = if (playerVel.y < -4f) (-playerVel.y * 0.45f).coerceAtMost(12f) else 6.8f
        val newVy = max(playerVel.y * 0.35f + 5.8f, 4.8f)

        playerVel.x += fwdX * diveBoost
        playerVel.y = newVy
        playerVel.z += fwdZ * diveBoost

        playerXzBody.linearVelocity = Vec2(playerVel.x, playerVel.z)
        playerYBody.linearVelocity = Vec2(0f, playerVel.y)
        return len
    }

    fun releaseSwingVault(playerVel: Vec3) {
        playerVel.y = max(playerVel.y + 7.2f, 12.0f)
        playerVel.x *= 1.15f
        playerVel.z *= 1.15f
        playerXzBody.linearVelocity = Vec2(playerVel.x, playerVel.z)
        playerYBody.linearVelocity = Vec2(0f, playerVel.y)
    }

    fun applyJumpOrWebZip(velX: Float, velY: Float, velZ: Float) {
        playerXzBody.linearVelocity = Vec2(velX, velZ)
        playerYBody.linearVelocity = Vec2(0f, velY)
    }

    fun step(
        dt: Float,
        playerPos: Vec3,
        playerVel: Vec3,
        moveStickX: Float,
        moveStickY: Float,
        cameraYaw: Float,
        isGrounded: Boolean,
        isSwinging: Boolean,
        swingTarget: Vec3,
        swingLength: Float,
        buildings: List<CityBuilding>,
        onPlayerYawUpdated: (Float) -> Unit,
        onStateUpdated: (grounded: Boolean, wallRunning: Boolean, newSwingLen: Float) -> Unit
    ): StepResult {
        val fwdX = -sin(cameraYaw)
        val fwdZ = -cos(cameraYaw)
        val rightX = cos(cameraYaw)
        val rightZ = -sin(cameraYaw)

        val wishX = rightX * moveStickX + fwdX * (-moveStickY)
        val wishZ = rightZ * moveStickX + fwdZ * (-moveStickY)
        val wishMag = sqrt(wishX * wishX + wishZ * wishZ)

        // Sync velocity from JBox2D bodies
        var vx = playerXzBody.linearVelocity.x
        var vz = playerXzBody.linearVelocity.y
        var vy = playerYBody.linearVelocity.y

        if (wishMag > 0.08f) {
            val accel = if (isGrounded) 58f else if (isSwinging) 30f else 24f
            vx += wishX * accel * dt
            vz += wishZ * accel * dt
            onPlayerYawUpdated(atan2(-wishX, -wishZ))
        } else if (isGrounded) {
            val damp = (1f - 9.5f * dt).coerceAtLeast(0f)
            vx *= damp
            vz *= damp
        }

        // Update dynamic rooftop support height underneath player in JBox2D yWorld
        var supportHeight = 0f
        for (b in buildings) {
            val halfW = b.width * 0.5f + 0.7f
            val halfD = b.depth * 0.5f + 0.7f
            if (abs(playerPos.x - b.x) < halfW && abs(playerPos.z - b.z) < halfD) {
                if (playerPos.y >= b.height - 1.35f) {
                    supportHeight = max(supportHeight, b.height)
                }
            }
        }
        floorSupportBody.setTransform(Vec2(0f, supportHeight - 1.0f), 0f)

        // Wall-run upward lift when touching a building face in JBox2D xzWorld
        val horizSpeed = sqrt(vx * vx + vz * vz)
        val wallRunningNow = wallContactCount > 0 && !isGrounded && horizSpeed > 6.0f
        if (wallRunningNow) {
            vy = max(vy, 5.4f)
        }

        // Clamp horizontal speed
        if (horizSpeed > maxHorizontalSpeed) {
            val s = maxHorizontalSpeed / horizSpeed
            vx *= s
            vz *= s
        }

        val prevVy = vy
        playerXzBody.linearVelocity = Vec2(vx, vz)
        playerYBody.linearVelocity = Vec2(0f, vy)

        // Step both JBox2D physics worlds
        currentPlayerY = playerPos.y
        xzWorld.step(dt, 6, 2)
        yWorld.step(dt, 6, 2)

        // Read resolved positions & velocities from JBox2D
        var px = playerXzBody.position.x
        var pz = playerXzBody.position.y
        var py = (playerYBody.position.y - 0.5f).coerceAtLeast(supportHeight)

        vx = playerXzBody.linearVelocity.x
        vz = playerXzBody.linearVelocity.y
        vy = playerYBody.linearVelocity.y

        // Pendulum web-swing constraint applied back into JBox2D state
        var updatedSwingLen = swingLength
        if (isSwinging) {
            val rx = px - swingTarget.x
            val ry = py - swingTarget.y
            val rz = pz - swingTarget.z
            val dist = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(0.01f)

            if (dist > updatedSwingLen) {
                val nx = rx / dist
                val ny = ry / dist
                val nz = rz / dist

                px = swingTarget.x + nx * updatedSwingLen
                py = (swingTarget.y + ny * updatedSwingLen).coerceAtLeast(supportHeight)
                pz = swingTarget.z + nz * updatedSwingLen

                val radialVel = vx * nx + vy * ny + vz * nz
                if (radialVel > 0f) {
                    vx -= radialVel * nx
                    vy -= radialVel * ny
                    vz -= radialVel * nz
                }

                vx += fwdX * swingPumpForce * dt
                vz += fwdZ * swingPumpForce * dt

                playerXzBody.setTransform(Vec2(px, pz), 0f)
                playerXzBody.linearVelocity = Vec2(vx, vz)
                playerYBody.setTransform(Vec2(0f, py + 0.5f), 0f)
                playerYBody.linearVelocity = Vec2(0f, vy)
            }

            if (py < 9f && updatedSwingLen > 12f) {
                updatedSwingLen -= 6.5f * dt
            }
        }

        playerPos.x = px
        playerPos.y = py
        playerPos.z = pz
        playerVel.x = vx
        playerVel.y = vy
        playerVel.z = vz

        val groundedNow = py <= supportHeight + 0.08f && vy <= 0.5f
        val landedHard = groundedNow && !isGrounded && prevVy < -17f

        onStateUpdated(groundedNow, wallRunningNow, updatedSwingLen)
        val totalSpeed = sqrt(vx * vx + vy * vy + vz * vz)
        return StepResult(landedHard = landedHard, speed = totalSpeed)
    }
}
