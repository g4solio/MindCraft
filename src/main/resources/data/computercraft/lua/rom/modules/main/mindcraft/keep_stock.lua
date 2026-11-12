-- Keep-stock module, version 1.
-- It keeps a minimum quantity of one item type in the destination station. The job has no end.
--
-- The module is a composition of the transport module. It adds one thing: the robot observes
-- the destination from its dock, and waits there while the stock is sufficient.
-- The runtime calculates the quantity to bring ("need"). The load in the robot counts as in transit.

local transport = require("mindcraft.transport")

local VERSION = "keep_stock@1"

local function run()
    mindcraft.attach(VERSION)
    while true do
        local job = mindcraft.status()
        if not transport.active(job) then return end
        if job.state ~= "RUNNING" then sleep(transport.POLL_SECONDS) end
        if job.carried > 0 or (job.need or 0) > 0 then
            if not transport.step(job) then return end
        else
            -- The destination stock is unknown or sufficient. The robot goes to the dock and observes it.
            if not transport.goTo(job.to) then return end
            mindcraft.observe()
        end
    end
end

return { run = run, version = VERSION }
