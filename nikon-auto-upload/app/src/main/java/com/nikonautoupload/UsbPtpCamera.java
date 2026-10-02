package com.nikonautoupload;

import android.hardware.usb.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generic USB Still-Image/PTP transport for Android USB Host mode.
 *
 * This class intentionally contains no Nikon-specific UI or Flickr logic.  It
 * talks standard PTP over USB bulk endpoints so other camera brands can use
 * the same module when they expose a compatible Still Image interface.
 */
public final class UsbPtpCamera implements Closeable {
    public interface ProgressListener {
        void onProgress(long done, long total);
    }

    public static final class PhotoObject {
        public final int handle;
        public final int storageId;
        public final int format;
        public final long size;
        public final String name;
        public final String captureDate;
        public final int width;
        public final int height;

        PhotoObject(int handle, int storageId, int format, long size, String name,
                    String captureDate, int width, int height) {
            this.handle=handle; this.storageId=storageId; this.format=format; this.size=size;
            this.name=name==null?"":name; this.captureDate=captureDate==null?"":captureDate;
            this.width=width; this.height=height;
        }

        public String signature() {
            return Integer.toUnsignedString(storageId)+":"+Integer.toUnsignedString(handle)+":"+size+":"+name;
        }

        public boolean isJpeg() {
            String x=name.toLowerCase(Locale.US);
            return format==0x3801 || x.endsWith(".jpg") || x.endsWith(".jpeg");
        }

        public boolean isRaw() {
            String x=name.toLowerCase(Locale.US);
            return x.endsWith(".nef") || x.endsWith(".nrw") || x.endsWith(".dng") ||
                    x.endsWith(".arw") || x.endsWith(".cr2") || x.endsWith(".cr3") ||
                    x.endsWith(".raf") || x.endsWith(".rw2") || x.endsWith(".orf") ||
                    x.endsWith(".pef") || x.endsWith(".srw");
        }
    }

    private static final int CONTAINER_COMMAND=1;
    private static final int CONTAINER_DATA=2;
    private static final int CONTAINER_RESPONSE=3;
    private static final int RESPONSE_OK=0x2001;

    private static final int OP_OPEN_SESSION=0x1002;
    private static final int OP_CLOSE_SESSION=0x1003;
    private static final int OP_GET_STORAGE_IDS=0x1004;
    private static final int OP_GET_OBJECT_HANDLES=0x1007;
    private static final int OP_GET_OBJECT_INFO=0x1008;
    private static final int OP_GET_OBJECT=0x1009;
    private static final int OP_DELETE_OBJECT=0x100B;

    private final UsbManager manager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface ptpInterface;
    private UsbEndpoint bulkIn, bulkOut, interruptIn;
    private int transactionId=1;
    private boolean sessionOpen;
    private final Map<Integer,PhotoObject> infoCache=new HashMap<>();

    public UsbPtpCamera(UsbManager manager) { this.manager=manager; }

    public static boolean isPtpDevice(UsbDevice d) {
        if(d==null)return false;
        if(d.getDeviceClass()==UsbConstants.USB_CLASS_STILL_IMAGE)return true;
        for(int i=0;i<d.getInterfaceCount();i++){
            UsbInterface f=d.getInterface(i);
            if(f.getInterfaceClass()==UsbConstants.USB_CLASS_STILL_IMAGE)return true;
        }
        return false;
    }

    public synchronized void open(UsbDevice d) throws IOException {
        close();
        if(d==null)throw new IOException("No USB camera supplied");
        if(!manager.hasPermission(d))throw new SecurityException("USB permission not granted");
        UsbInterface found=null;
        for(int i=0;i<d.getInterfaceCount();i++){
            UsbInterface f=d.getInterface(i);
            if(f.getInterfaceClass()==UsbConstants.USB_CLASS_STILL_IMAGE){found=f;break;}
        }
        if(found==null && d.getDeviceClass()==UsbConstants.USB_CLASS_STILL_IMAGE && d.getInterfaceCount()>0)found=d.getInterface(0);
        if(found==null)throw new IOException("Camera does not expose a USB PTP/Still Image interface");

        UsbEndpoint in=null,out=null,intr=null;
        for(int i=0;i<found.getEndpointCount();i++){
            UsbEndpoint ep=found.getEndpoint(i);
            if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){
                if(ep.getDirection()==UsbConstants.USB_DIR_IN)in=ep; else out=ep;
            }else if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_INT && ep.getDirection()==UsbConstants.USB_DIR_IN)intr=ep;
        }
        if(in==null||out==null)throw new IOException("Camera PTP bulk endpoints were not found");

        UsbDeviceConnection c=manager.openDevice(d);
        if(c==null)throw new IOException("Android could not open the USB camera");
        if(!c.claimInterface(found,true)){c.close();throw new IOException("Android could not claim the camera PTP interface");}

        device=d; connection=c; ptpInterface=found; bulkIn=in; bulkOut=out; interruptIn=intr;
        transactionId=1; infoCache.clear();
        int response=executeNoDataWithTx(OP_OPEN_SESSION,0,1);
        if(response!=RESPONSE_OK){close();throw new IOException("Camera rejected PTP OpenSession: 0x"+Integer.toHexString(response));}
        sessionOpen=true;
    }

    public UsbDevice getDevice(){return device;}
    public boolean isOpen(){return connection!=null&&sessionOpen;}

    public synchronized List<PhotoObject> listPhotos() throws IOException {
        ensureOpen();
        int[] storages=parseU32Array(executeData(OP_GET_STORAGE_IDS));
        LinkedHashSet<Integer> handles=new LinkedHashSet<>();
        for(int storage:storages){
            byte[] data=executeData(OP_GET_OBJECT_HANDLES,storage,0,0);
            for(int h:parseU32Array(data))handles.add(h);
        }
        infoCache.keySet().retainAll(handles);
        ArrayList<PhotoObject> photos=new ArrayList<>();
        for(int handle:handles){
            PhotoObject obj=infoCache.get(handle);
            if(obj==null){
                try{obj=parseObjectInfo(handle,executeData(OP_GET_OBJECT_INFO,handle));infoCache.put(handle,obj);}
                catch(Exception ignored){continue;}
            }
            if(isPhotoName(obj.name)||obj.format==0x3801)photos.add(obj);
        }
        photos.sort((a,b)->Integer.compareUnsigned(b.handle,a.handle));
        return photos;
    }

    public synchronized void download(PhotoObject photo, File destination, ProgressListener progress) throws IOException {
        ensureOpen();
        if(photo==null)throw new IOException("No photo selected");
        File parent=destination.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Could not create import folder");
        int tx=nextTx();
        sendCommand(OP_GET_OBJECT,tx,photo.handle);
        Header first=readHeader(30000);
        if(first.type==CONTAINER_RESPONSE){
            drain(first.length-12,30000);throw new IOException("GetObject failed: 0x"+Integer.toHexString(first.code));
        }
        if(first.type!=CONTAINER_DATA||first.code!=OP_GET_OBJECT||first.tx!=tx)throw new IOException("Unexpected PTP data container while downloading");
        long total=Integer.toUnsignedLong(first.length)-12L;
        long done=0;
        byte[] buffer=new byte[256*1024];
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(destination))){
            long remaining=total;
            while(remaining>0){
                int want=(int)Math.min(buffer.length,remaining);
                int n=bulkRead(buffer,0,want,30000);
                if(n<=0)throw new EOFException("USB photo transfer stopped early");
                out.write(buffer,0,n); remaining-=n; done+=n;
                if(progress!=null)progress.onProgress(done,total);
            }
        }catch(Exception e){destination.delete();throw e;}
        Header response=readHeader(30000);
        if(response.length>12)drain(response.length-12,30000);
        if(response.type!=CONTAINER_RESPONSE||response.tx!=tx||response.code!=RESPONSE_OK){destination.delete();throw new IOException("Camera reported transfer failure: 0x"+Integer.toHexString(response.code));}
        if(progress!=null)progress.onProgress(total,total);
    }

    public synchronized void delete(PhotoObject photo) throws IOException {
        ensureOpen();
        int response=executeNoData(OP_DELETE_OBJECT,photo.handle,0);
        if(response!=RESPONSE_OK)throw new IOException("DeleteObject failed: 0x"+Integer.toHexString(response));
        infoCache.remove(photo.handle);
    }

    private byte[] executeData(int op,int... params) throws IOException {
        int tx=nextTx();
        sendCommand(op,tx,params);
        Header first=readHeader(15000);
        if(first.type==CONTAINER_RESPONSE){
            if(first.length>12)drain(first.length-12,15000);
            throw new IOException("PTP operation 0x"+Integer.toHexString(op)+" failed: 0x"+Integer.toHexString(first.code));
        }
        if(first.type!=CONTAINER_DATA||first.code!=op||first.tx!=tx)throw new IOException("Unexpected PTP data response for 0x"+Integer.toHexString(op));
        int payload=first.length-12;
        if(payload<0||payload>64*1024*1024)throw new IOException("PTP response is too large: "+payload);
        byte[] data=new byte[payload];
        readFully(data,0,payload,30000);
        Header response=readHeader(15000);
        if(response.length>12)drain(response.length-12,15000);
        if(response.type!=CONTAINER_RESPONSE||response.tx!=tx||response.code!=RESPONSE_OK)throw new IOException("PTP response 0x"+Integer.toHexString(response.code));
        return data;
    }

    private int executeNoData(int op,int... params) throws IOException {
        return executeNoDataWithTx(op,nextTx(),params);
    }

    private int executeNoDataWithTx(int op,int tx,int... params) throws IOException {
        sendCommand(op,tx,params);
        Header response=readHeader(15000);
        if(response.length>12)drain(response.length-12,15000);
        if(response.type!=CONTAINER_RESPONSE||response.tx!=tx)throw new IOException("Unexpected PTP command response");
        return response.code;
    }

    private int nextTx(){return transactionId++;}

    private void sendCommand(int op,int tx,int... params) throws IOException {
        int count=params==null?0:params.length;
        ByteBuffer b=ByteBuffer.allocate(12+count*4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(b.capacity()).putShort((short)CONTAINER_COMMAND).putShort((short)op).putInt(tx);
        if(params!=null)for(int p:params)b.putInt(p);
        bulkWrite(b.array(),0,b.capacity(),15000);
    }

    private Header readHeader(int timeout) throws IOException {
        byte[] h=new byte[12]; readFully(h,0,12,timeout);
        ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int len=b.getInt(); int type=b.getShort()&0xffff; int code=b.getShort()&0xffff; int tx=b.getInt();
        if(len<12)throw new IOException("Invalid PTP container length "+len);
        return new Header(len,type,code,tx);
    }

    private void drain(int bytes,int timeout) throws IOException {
        byte[] buf=new byte[Math.min(64*1024,Math.max(1,bytes))]; int remain=bytes;
        while(remain>0){int n=bulkRead(buf,0,Math.min(buf.length,remain),timeout);if(n<=0)throw new EOFException();remain-=n;}
    }

    private void readFully(byte[] out,int off,int len,int timeout) throws IOException {
        int pos=0;
        while(pos<len){int n=bulkRead(out,off+pos,len-pos,timeout);if(n<=0)throw new EOFException("USB camera stopped responding");pos+=n;}
    }

    private int bulkRead(byte[] buffer,int offset,int len,int timeout) throws IOException {
        if(connection==null)throw new IOException("USB camera disconnected");
        int n=connection.bulkTransfer(bulkIn,buffer,offset,len,timeout);
        if(n<0)throw new IOException("USB read failed"); return n;
    }

    private void bulkWrite(byte[] buffer,int offset,int len,int timeout) throws IOException {
        int pos=0;
        while(pos<len){
            if(connection==null)throw new IOException("USB camera disconnected");
            int n=connection.bulkTransfer(bulkOut,buffer,offset+pos,len-pos,timeout);
            if(n<=0)throw new IOException("USB write failed"); pos+=n;
        }
    }

    private int[] parseU32Array(byte[] data) throws IOException {
        if(data.length<4)throw new IOException("PTP array is missing its count");
        ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); long count=Integer.toUnsignedLong(b.getInt());
        if(count>(data.length-4)/4)throw new IOException("PTP array count is invalid");
        int[] out=new int[(int)count]; for(int i=0;i<out.length;i++)out[i]=b.getInt(); return out;
    }

    private PhotoObject parseObjectInfo(int handle,byte[] data) throws IOException {
        if(data.length<53)throw new IOException("ObjectInfo too short");
        ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int storage=b.getInt(); int format=b.getShort()&0xffff; b.getShort(); long size=Integer.toUnsignedLong(b.getInt());
        b.getShort(); b.getInt(); b.getInt(); b.getInt(); int width=b.getInt(); int height=b.getInt(); b.getInt();
        b.getInt(); b.getShort(); b.getInt(); b.getInt();
        String name=ptpString(b); String capture=ptpString(b); ptpString(b); ptpString(b);
        if(name==null||name.trim().isEmpty())name="OBJECT_"+Integer.toUnsignedString(handle);
        return new PhotoObject(handle,storage,format,size,name,capture,width,height);
    }

    private String ptpString(ByteBuffer b){
        if(!b.hasRemaining())return ""; int chars=b.get()&0xff; if(chars==0)return "";
        int bytes=Math.min(b.remaining(),chars*2); if(bytes<=0)return "";
        byte[] raw=new byte[bytes];b.get(raw); int useful=Math.max(0,raw.length-2);
        return new String(raw,0,useful,StandardCharsets.UTF_16LE).replace("\u0000","").trim();
    }

    private boolean isPhotoName(String name){
        String x=name==null?"":name.toLowerCase(Locale.US);
        return x.endsWith(".jpg")||x.endsWith(".jpeg")||x.endsWith(".nef")||x.endsWith(".nrw")||
                x.endsWith(".dng")||x.endsWith(".tif")||x.endsWith(".tiff")||x.endsWith(".arw")||
                x.endsWith(".cr2")||x.endsWith(".cr3")||x.endsWith(".raf")||x.endsWith(".rw2")||
                x.endsWith(".orf")||x.endsWith(".pef")||x.endsWith(".srw")||x.endsWith(".png");
    }

    private void ensureOpen() throws IOException { if(!isOpen())throw new IOException("USB camera is not connected"); }

    @Override public synchronized void close() {
        try{if(connection!=null&&sessionOpen){try{executeNoData(OP_CLOSE_SESSION);}catch(Exception ignored){}}}catch(Exception ignored){}
        sessionOpen=false;
        if(connection!=null&&ptpInterface!=null){try{connection.releaseInterface(ptpInterface);}catch(Exception ignored){}}
        if(connection!=null)try{connection.close();}catch(Exception ignored){}
        connection=null;ptpInterface=null;bulkIn=null;bulkOut=null;interruptIn=null;device=null;infoCache.clear();
    }

    private static final class Header {
        final int length,type,code,tx;
        Header(int length,int type,int code,int tx){this.length=length;this.type=type;this.code=code;this.tx=tx;}
    }
}
