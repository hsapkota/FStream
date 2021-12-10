## FStream++


### Reqiuriments
- Linux environment. 
- This project runs on XSEDE Grid computer networks. Also, it uses `myproxy-logon` certificate.<br>
```myproxy-logon -s myproxy.xsede.org -l [username] -t 9999```

### Parameters
```
-s				: source path
-d				: destination path
-rtt				: round trip time 
-bandwidth [value]		: user defined bw value
-profiling			: activate Profiling transfer
-static				: activate Static transfer
-qos				: activate Quality of Service
-speedLimit [value]		: set QoS speed limit
-buffer-size
```
