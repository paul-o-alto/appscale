# Monit watches the Search Server, so it will restart after being killed.
ps aux | grep ml_server | grep -v grep | awk '{print $2}' | xargs kill -9
