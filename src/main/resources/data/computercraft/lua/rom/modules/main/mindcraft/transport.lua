-- Transport module, version 1.
-- It moves one item type from the source station to the destination station.
--
-- The module keeps no state. It reads the job state from the runtime before each step.
-- Thus it can start again at any time, for example after a restart.
-- The runtime does all item transfers (mindcraft.take, mindcraft.give) and counts them.
-- The module moves the turtle with the standard turtle API. It does not dig or attack.
--
-- Limit: the path between the docks must be free on straight lines. There is no path search.

local VERSION = "transport@1"
local MOVE_RETRIES = 10 -- attempts before the module reports an impediment
local MOVE_RETRY_SECONDS = 1
local POLL_SECONDS = 5 -- time between two attempts when a station cannot give or accept items

local TURNS = { north = 0, east = 1, south = 2, west = 3 }

local function active(job)
    return job and (job.state == "RUNNING" or job.state == "WAITING" or job.state == "SATISFIED")
end

local function face(direction)
    local _, _, _, current = mindcraft.pose()
    local turns = (TURNS[direction] - TURNS[current]) % 4
    if turns == 3 then
        turtle.turnLeft()
    else
        for _ = 1, turns do turtle.turnRight() end
    end
end

-- Makes one move toward the target. Returns "arrived", "moved", or nil and the reason.
local function step(target)
    local x, y, z = mindcraft.pose()
    if x == target.x and y == target.y and z == target.z then return "arrived" end
    local ok, err
    if x ~= target.x then
        face(target.x > x and "east" or "west")
        ok, err = turtle.forward()
        if ok then return "moved" end
    end
    if z ~= target.z then
        face(target.z > z and "south" or "north")
        ok, err = turtle.forward()
        if ok then return "moved" end
    end
    if y ~= target.y then
        if target.y > y then ok, err = turtle.up() else ok, err = turtle.down() end
        if ok then return "moved" end
    end
    return nil, err
end

-- Refuels from items in the turtle. It never burns the job item, because the runtime counts that item.
-- Returns true when the turtle has fuel.
local function refuel(jobItem)
    for slot = 1, 16 do
        local detail = turtle.getItemDetail(slot)
        if detail and detail.name ~= jobItem then
            turtle.select(slot)
            turtle.refuel()
        end
    end
    turtle.select(1)
    return turtle.getFuelLevel() == "unlimited" or turtle.getFuelLevel() > 0
end

-- Goes to the target. Returns true on arrival. Returns false when the job stops or an impediment remains.
local function goTo(target)
    local failures = 0
    while true do
        -- This call also lets the owner pause or cancel between two moves.
        local job = mindcraft.status()
        if not active(job) then return false end
        local result, err = step(target)
        if result == "arrived" then return true end
        if result == "moved" then
            failures = 0
        else
            failures = failures + 1
            local noFuel = err == "Out of fuel"
            if (noFuel and not refuel(job.item)) or failures >= MOVE_RETRIES then
                local x, y, z, facing = mindcraft.pose()
                local found, block = turtle.inspect()
                mindcraft.block(("the robot cannot move from %d %d %d (%s, in front: %s) toward %d %d %d: %s"):format(
                    x, y, z, facing, found and block.name or "no block", target.x, target.y, target.z, tostring(err)))
                return false
            end
            if not noFuel then sleep(MOVE_RETRY_SECONDS) end
        end
    end
end

-- One transport step: deliver the load, or get what the job needs. Returns false when the job stops.
-- The step comes from the observed load, not from a saved program position.
local function step(job)
    local deliver = job.carried > 0
    if not goTo(deliver and job.to or job.from) then return false end
    if deliver then mindcraft.give() else mindcraft.take() end
    return true
end

local function run()
    mindcraft.attach(VERSION)
    while true do
        local job = mindcraft.status()
        if not active(job) then return end
        if job.state == "WAITING" then sleep(POLL_SECONDS) end
        if not step(job) then return end
    end
end

-- Other modules build on "step", "goTo" and "active". See keep_stock.lua.
return { run = run, step = step, goTo = goTo, active = active, version = VERSION, POLL_SECONDS = POLL_SECONDS }
