-- MindCraft agent. CraftOS runs this file on each computer at startup, before the startup files of the player.
-- The agent keeps control only on a turtle that is enrolled as a MindCraft robot.
-- On all other computers it returns immediately.
if not mindcraft or not mindcraft.enrolled() then return end

term.clear()
term.setCursorPos(1, 1)
print("MindCraft: this turtle works for Uno.")
print("Hold Ctrl+T to stop the agent.")

while true do
    local job = mindcraft.status()
    if job and (job.state == "RUNNING" or job.state == "WAITING" or job.state == "SATISFIED") then
        -- The job names its module: "transport" or "keep_stock".
        local ok, err = pcall(function() require("mindcraft." .. job.module).run() end)
        if not ok then
            if err == "Terminated" then error(err, 0) end
            -- A module error must be visible to the owner. It must not kill the agent.
            pcall(mindcraft.block, "module error: " .. tostring(err))
        end
    else
        -- The runtime queues a "mindcraft" event when the job changes.
        -- The timer is a safety net for a lost event.
        local timer = os.startTimer(10)
        repeat
            local event, id = os.pullEvent()
        until event == "mindcraft" or (event == "timer" and id == timer)
    end
end
