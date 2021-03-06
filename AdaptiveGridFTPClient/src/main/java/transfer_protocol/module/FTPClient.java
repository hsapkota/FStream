package transfer_protocol.module;

import client.AdaptiveGridFTPClient;
import client.FileCluster;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.globus.ftp.HostPort;
import org.globus.ftp.vanilla.Reply;
import transfer_protocol.util.AdSink;
import transfer_protocol.util.StorkUtil;
import transfer_protocol.util.TransferProgress;
import transfer_protocol.util.XferList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

// A custom extended GridFTPClient that implements some undocumented
// operations and provides some more responsive transfer methods.
public class FTPClient {
    private HashSet<String> pipingPathSet = new HashSet<>();
    volatile boolean aborted = false;
    private FTPURI su, du;
    private TransferProgress progress = new TransferProgress();
    //private AdSink sink = null;
    //private FTPServerFacade local;
    private ChannelModule.ChannelPair cc;  // Main control channels.
    private boolean enableIntegrityVerification = false;
    public LinkedList<FileCluster> fileClusters;
    public List<ChannelModule.ChannelPair> channelList;

    private static final Log LOG = LogFactory.getLog(FTPClient.class);

    public FTPClient(FTPURI su, FTPURI du) throws Exception {
        this.su = su;
        this.du = du;
        cc = new ChannelModule.ChannelPair(su, du);
        channelList = new LinkedList<>();
    }

    // Set the progress listener for this ftpClient's transfers.
    public void setAdSink(AdSink sink) {
        //this.sink = sink;
        progress.attach(sink);
    }


    public int getChannelCount() {
        return channelList.size();
    }


    void close() {
        cc.close();
    }

    // Recursively list directories.
    public XferList mlsr(HashSet<String> prevList) throws Exception {
        final String MLSR = "MLSR", MLSD = "MLSD";
        final int MAXIMUM_PIPELINING = 200;
        int currentPipelining = 0;
        //String cmd = isFeatureSupported("MLSR") ? MLSR : MLSD;
        String cmd = MLSD;
        XferList list = new XferList(su.path, du.path);
        String path = list.sp;
        // Check if we need to do a local listing.
        if (cc.sc.local) {
            return StorkUtil.list(path);
        }
        ChannelModule.ChannelPair cc = new ChannelModule.ChannelPair(this.cc.sc);

        LinkedList<String> dirs = new LinkedList<String>();
        dirs.add("");
        cc.rc.exchange("OPTS MLST type;size;");
        //reply = cc.rc.exchange("PBSZ 1048576;");
        //System.out.println(reply.getCode() + "\t" + reply.getMessage());
        // Keep listing and building subdirectory lists.

        // TODO: Replace with pipelining structure.
        LinkedList<String> waiting = new LinkedList<String>();
        LinkedList<String> working = new LinkedList<String>();
        while (!dirs.isEmpty() || !waiting.isEmpty()) {
            LinkedList<String> subdirs = new LinkedList<String>();

            while (!dirs.isEmpty())
                waiting.add(dirs.pop());

            // Pipeline commands like a champ.
            while (currentPipelining < MAXIMUM_PIPELINING && !waiting.isEmpty()) {
                String p = waiting.pop();
                cc.pipePassive();
                String fullPath = path + p;
                if (!pipingPathSet.contains(fullPath)) {
                    System.out.println("Piping  " + cmd + " " + path + p);
                    LOG.info("Piping  " + cmd + " " + path + p);
                    pipingPathSet.add(fullPath);
                }
                cc.rc.write(cmd, path + p);
                working.add(p);
                currentPipelining++;
            }

            // Read the pipelined responses like a champ.
            for (String p : working) {
                ChannelModule.ListSink sink = new ChannelModule.ListSink(path);
                // Interpret the pipelined PASV command.
                try {
                    HostPort hp = cc.getPasvReply();
                    cc.setActive(hp);
                } catch (Exception e) {
                    sink.close();
                    throw new Exception("couldn't set passive mode: " + e);
                }

                // Try to get the listing, ignoring errors unless it was root.
                try {
                    cc.oc.facade.store(sink);
                    cc.watchTransfer(null, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (p.isEmpty()) {
                        throw new Exception("couldn't list: " + path + ": " + e);
                    }
                    continue;
                }

                XferList xl = sink.getList(p, prevList);

                // If we did mlsr, return the list.
                if (cmd == MLSR) {
                    return xl;
                }
                // Otherwise, add subdirs and repeat.
                for (XferList.MlsxEntry e : xl) {
                    if (e.dir) {
                        subdirs.add(e.spath);
                    }
                }
                list.addAll(xl);

            }
            working.clear();
            currentPipelining = 0;

            // Get ready to repeat with new subdirs.
            dirs.addAll(subdirs);
        }

        return list;
    }

    // Get the size of a file.
    public long size(String path) throws Exception {
        if (cc.sc.local) {
            return StorkUtil.size(path);
        }
        Reply r = cc.sc.exchange("SIZE", path);
        if (!Reply.isPositiveCompletion(r)) {
            throw new Exception("file does not exist: " + path);
        }
        return Long.parseLong(r.getMessage());
    }

    public void setEnableIntegrityVerification(boolean enableIntegrityVerification) {
        this.enableIntegrityVerification = enableIntegrityVerification;
    }


    // Call this to kill transfer.
    public void abort() {
        for (ChannelModule.ChannelPair cc : channelList)
            cc.abort();
        aborted = true;
    }

    // Check if we're prepared to transfer a file. This means we haven't
    // aborted and destination has been properly set.
    void checkTransfer() throws Exception {
        if (aborted) {
            throw new Exception("transfer aborted");
        }
    }


    //returns list of files to be transferred
    public XferList getListofFiles(String sp, String dp, HashSet<String> prevList) throws Exception {
        checkTransfer();

        checkTransfer();
        XferList xl;
        // Some quick sanity checking.
        if (sp == null || sp.isEmpty()) {
            throw new Exception("src spath is empty");
        }
        if (dp == null || dp.isEmpty()) {
            throw new Exception("dest spath is empty");
        }
        // See if we're doing a directory transfer and need to build
        // a directory list.
        if (sp.endsWith("/")) {
            xl = mlsr(prevList);
            xl.dp = dp;
        } else {  // Otherwise it's just one file.
            xl = new XferList(sp, dp, size(sp));
        }
        // Pass the list off to the transfer() which handles lists.
        xl.updateDestinationPaths();
        for (XferList.MlsxEntry entry : xl.getFileList()) {
            if (entry.dir) {
//                LOG.info("Creating directory " + entry.dpath);
                cc.pipeMkdir(entry.dpath);
                //cc.watchTransfer(null, null);
            }
        }
        return xl;
    }

    // Transfer a list over a channel.
    public void transferList(ChannelModule.ChannelPair cc) throws Exception {
        checkTransfer();
        //add first piped file to onAir list
        XferList fileList = cc.chunk.getRecords();
        // updateOnAir(fileList, +1);
        // pipe transfer commands if ppq is enabled
//        LOG.info("Channel " + cc.getId() + " is starting to transfer files");
        for (int i = cc.inTransitFiles.size(); i < cc.getPipelining() + 1; i++) {
            pullAndSendAFile(cc);
        }
        while (cc.inTransitFiles.size() != 0) {
            fileList = cc.chunk.getRecords();
            // Read responses to piped commands.
            XferList.MlsxEntry e = cc.inTransitFiles.poll();
            // if(cc.isMarkedAsRemove()){
            //     System.out.println(cc.getId() + " is marked as remove and the size of inTransitFiles is: "+cc.inTransitFiles.size());
            // }
            if (e != null && e.dir) {
                try {
                    if (!cc.dc.local) {
                        cc.dc.read();
                    }
                } catch (Exception ex) {
                }
            } else {
                ChannelModule.ProgressListener prog = new ChannelModule.ProgressListener(this);
                cc.watchTransfer(prog, e);
                if (e.len == -1) {
                    updateChunk(fileList, e.size - prog.last_bytes);
                } else {
                    updateChunk(fileList, e.len - prog.last_bytes);
                }
                // if(cc.isMarkedAsRemove()){
                //     System.out.println(cc.getId() + " is marked as remove at line 253");
                // }
                updateOnAir(fileList, -1);
                if (!cc.isConfigurationChanged && !cc.isMarkedAsRemove()) {
                    for (int i = cc.inTransitFiles.size(); i < cc.getPipelining() + 1; i++) {
                        pullAndSendAFile(cc);
                    }
                }
            }
            // The transfer of the channel's assigned fileClusters is completed.
            // Check if other fileClusters have any outstanding files. If so, help!

            
        }
        
        //LOG.info(cc.id + "--Chunk "+ cc.xferListIndex + "finished " +fileClusters.get(cc.xferListIndex).count());
        // System.out.println("Transfer " + AdaptiveGridFTPClient.TRANSFER_NUMBER + " completed in channel : "
        //         + cc.getId() + " for " + cc.chunk.getDensity().toString() + " chunk. removing from channel list.");
        boolean removed = fileList.channels.remove(cc);
        System.out.println("Removed the channel id: "+cc.getId()+": " + removed);
        AdaptiveGridFTPClient.channelInUse.remove(cc);

        if (cc.getChunkType().equals("SMALL")) {
            AdaptiveGridFTPClient.smallMarkedChannels.put(cc, false);
        } else {
            AdaptiveGridFTPClient.largeMarkedChannels.put(cc, false);
        }
        cc.setMarkedAsRemove(false);
        return;
        

    }

    public ChannelModule.ChannelPair restartChannel(ChannelModule.ChannelPair oldChannel) {
        System.out.println("Updating channel " + oldChannel.getId() + " parallelism to " +
                oldChannel.newChunk.getTunableParameters().getParallelism());
        XferList oldFileList = oldChannel.chunk.getRecords();
        XferList newFileList = oldChannel.newChunk.getRecords();
        XferList.MlsxEntry fileToStart = getNextFile(newFileList);
        if (fileToStart == null) {
            return null;
        }

        synchronized (oldFileList) {
            oldFileList.channels.remove(oldChannel);
        }

        ChannelModule.ChannelPair newChannel;
        if (Math.abs(oldChannel.chunk.getTunableParameters().getParallelism() -
                oldChannel.newChunk.getTunableParameters().getParallelism()) > 1) {
//            oldChannel.close();
            newChannel = new ChannelModule.ChannelPair(su, du);
            boolean success = false;
            if(!newChannel.channnelConnectionFailed){
                success = GridFTPClient.setupChannelConf(newChannel, oldChannel.getId(), oldChannel.newChunk, fileToStart);
            }
            if (!success) {
                synchronized (newFileList) {
                    newFileList.addEntry(fileToStart);
                    return null;
                }
            }
        } else {
            oldChannel.chunk = oldChannel.newChunk;
            oldChannel.setPipelining(oldChannel.newChunk.getTunableParameters().getPipelining());
            oldChannel.pipeTransfer(fileToStart);
            oldChannel.inTransitFiles.add(fileToStart);
            synchronized(oldFileList) {
                oldFileList.onAir += 1;
            }
            newChannel = oldChannel;
        }

        synchronized (newFileList.channels) {
            newFileList.channels.add(newChannel);
        }
        updateOnAir(newFileList, +1);
        return newChannel;
    }

    XferList.MlsxEntry getNextFile(XferList fileList) {
        synchronized (fileList) {
            if (fileList.count() > 0) {
                return fileList.pop();
            }
        }
        return null;
    }

    void updateOnAir(XferList fileList, int count) {
        synchronized (fileList) {
            fileList.onAir += count;
        }
    }

    void updateChunk(XferList fileList, long count) {
        synchronized (fileList) {
            fileList.totalTransferredSize += count;
        }
    }

    private final boolean pullAndSendAFile(ChannelModule.ChannelPair cc) {
        XferList.MlsxEntry e;
        if ((e = getNextFile(cc.chunk.getRecords())) == null) {
            return false;
        }
        cc.pipeTransfer(e);
        cc.inTransitFiles.add(e);
        updateOnAir(cc.chunk.getRecords(), +1);
        return true;
    }

    synchronized ChannelModule.ChannelPair findChunkInNeed(ChannelModule.ChannelPair cc) throws Exception {
        double max = -1;
        boolean found = false;

        while (!found) {
            int index = -1;
            //System.out.println("total fileClusters:"+fileClusters.size());
            for (int i = 0; i < fileClusters.size(); i++) {
          /* Conditions to pick a chunk to allocate finished channel
             1- Chunk is ready to be transferred (non-SingleChunk algo)
             2- Chunk has still files to be transferred
             3- Chunks has the highest estimated finish time
          */
                if (fileClusters.get(i).isReadyToTransfer && fileClusters.get(i).getRecords().count() > 0 &&
                        fileClusters.get(i).getRecords().estimatedFinishTime > max) {
                    max = fileClusters.get(i).getRecords().estimatedFinishTime;
                    index = i;
                }
            }
            // not found any chunk candidate, returns
            if (index == -1) {

                if (cc.getChunkType().equals("SMALL")) {
                    AdaptiveGridFTPClient.smallMarkedChannels.put(cc, false);
                } else {
                    AdaptiveGridFTPClient.largeMarkedChannels.put(cc, false);
                }

                return null;
            }
            if (fileClusters.get(index).getRecords().count() > 0) {
                cc.newChunk = fileClusters.get(index);
                System.out.println("Channel  " + cc.getId() + " is being transferred from " +
                        cc.chunk.getDensity().name() + " to " + cc.newChunk.getDensity().name() +
                        "\t" + cc.newChunk.getTunableParameters().toString());
                cc = restartChannel(cc);
                System.out.println("Channel  " + cc.getId() + " is transferred current:" +
                        cc.inTransitFiles.size() + " ppq:" + cc.getPipelining());
                if (cc.inTransitFiles.size() > 0) {
                    return cc;
                }
            }
        }
        return null;
    }

    public void checkEstimatedTimeForParallelismChange(FileCluster chunk, GridFTPClient gridFTPClient) {
        System.out.println("Check estimate times starts. Will waiting for 5 secs...");

//        if (chunk.getRecords().estimatedFinishTime != Integer.MAX_VALUE /*&&
//            chunk.getRecords().estimatedFinishTime > 200*/) {
        System.err.println("Estimated time = " + chunk.getRecords().estimatedFinishTime
                + " " + chunk.getRecords().density.toString()
                + " chunk parallism change required. ");

        ArrayList<ChannelModule.ChannelPair> list = new ArrayList<>();
        for (int i = 0; i < chunk.getRecords().channels.size(); i++) {
            System.out.println("Channel = " + chunk.getRecords().channels.get(0).getId() + " has intransit = " + chunk.getRecords().channels.get(0).inTransitFiles.size());
            System.out.println("Channel = " + chunk.getRecords().channels.get(0).getId() + " is going to restart...");
            System.out.println("Sync for channel id " + i + " has been started");
            synchronized (chunk.getRecords().channels.get(i)) {
                System.out.println("[SYNC] Channel = " + chunk.getRecords().channels.get(i) + " going to mark as restart..");
                chunk.getRecords().channels.get(i).setMarkedAsRemove(true);
                list.add(chunk.getRecords().channels.get(i));
            }
            System.out.println("Sync for channel id " + i + " has been done");
        }
        while (list.size() != 0) {
            if (list.get(0).inTransitFiles.size() == 0) {
                AdaptiveGridFTPClient.smallMarkedChannels.remove(list.get(0));
                AdaptiveGridFTPClient.largeMarkedChannels.remove(list.get(0));
                list.remove(0);
            }
        }
        System.err.println("CHANNEL SIZE AFTER REMOVE = " + chunk.getRecords().channels.size());
        if (chunk.getRecords().channels.size() == 0) {
            System.out.println("Channel size is 0 now. Run with new channels.. ");
            gridFTPClient.runTransfer(chunk);
        } else {
            System.out.println("Chunk still has some channels so don;t know what to do .. ");
        }

//        } else {
//            System.out.println("No need to change parallelism for  " + chunk.getDensity().toString());
//        }
    }

}
