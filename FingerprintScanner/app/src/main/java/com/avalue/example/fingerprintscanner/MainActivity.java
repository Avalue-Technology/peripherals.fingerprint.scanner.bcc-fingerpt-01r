// region Header Remark
/**
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Name : MainActivity
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Purpose : MainActivity Class
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Class Method
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Dependent Reference
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * (01)Module: usb-serial-for-android
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Known Issues
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Methodology
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * References
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Android documents
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Internal documents
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * (01)In order to cut down the communication time when uploading or downloading image through UART interface, only the upper 4 bits of the pixel byte are applied, i.e. to combine two pixels bytes into one byte in transmitting.
 -------------------------------------------------------------------------------------------------------------------------------------->
 * Internet documents
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * usb-serial-for-android
 * https://github.com/mik3y/usb-serial-for-android
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Create Date : 2023-09-07										Create User : Alex Chang
 * -------------------------------------------------------------------------------------------------------------------------------------->
 * Update Date : 2023-09-07										Create User : Alex Chang
 * -------------------------------------------------------------------------------------------------------------------------------------->
 */
// endregion

// Define Package Name
package com.avalue.example.fingerprintscanner;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity Class
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, SerialInputOutputManager.Listener
{
    // region Class Variable
    private static final String ACTION_USB_PERMISSION = "com.avalue.example.fingerprintscanner.USB_PERMISSION";
    private static final char[] _HexCharsMappingArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};

    private static final int FINGERPRINT_BITMAP_WIDTH = 256;
    private static final int FINGERPRINT_BITMAP_HEIGHT = 360;
    private static final int DUMP_FINGERPRINT_PACKET_LENGTH = 100104;

    private static final int WRITE_WAIT_MILLIS = 5000;

    private UsbManager _UsbManager;
    private List<UsbSerialDriver> _UsbSerialDrivers;
    private UsbSerialDriver _UsbSerialDriver;
    private UsbDeviceConnection _UsbDeviceConnection;
    private UsbSerialPort _UsbSerialPort;
    private SerialInputOutputManager _SerialInputOutputManager;

    private boolean _IsUsbSerialPortConnected = false;
    private String _UsbSerialPortCommand = "";
    private boolean _IsFingerprintScanned = false;

    private StringBuilder _DumpFingerprintRawStringBuilder;

    private PendingIntent _UsbPermissionPendingIntent;
    private TextView _MessageTextView;
    private Button _ClearButton;
    private Button _OpenButton;
    private Button _ScanButton;
    private Button _DumpButton;
    private Button _CloseButton;
    private ImageView _FingerprintImageView;
    // endregion

    // region Class Method
    /**
     * Convert Hexadecimal String to Bytes Array
      * @param HexString
     * @return
     */
    private byte[] ConvertHexStringToBytesArray(String HexString)
    {
        int Length = HexString.length();
        byte[] BytesArray = new byte[Length/2];

        for(int LoopI = 0; LoopI < Length; LoopI+=2)
        {
            BytesArray[LoopI/2] = (byte) ((Character.digit(HexString.charAt(LoopI), 16) << 4) + Character.digit(HexString.charAt(LoopI+1), 16));
        }

        return BytesArray;
    }

    /**
     * Convert Bytes Array to Hexadecimal String
     * @param BytesArray
     * @return
     */
    public static String ConvertBytesArrayToHexString(byte[] BytesArray)
    {
        char[] HexChars = new char[BytesArray.length*2];
        int HexCharMappingIndex;

        for(int LoopI=0; LoopI < BytesArray.length; LoopI++)
        {
            HexCharMappingIndex = BytesArray[LoopI] & 0xFF;
            HexChars[LoopI*2] = _HexCharsMappingArray[HexCharMappingIndex>>>4];
            HexChars[LoopI*2 + 1] = _HexCharsMappingArray[HexCharMappingIndex & 0x0F];
        }

        return new String(HexChars);
    }

    /**
     * Split String By Chunk Size
     * @param SourceString
     * @param ChunkSize
     * @return
     */
    private List<String> SplitStringByChunkSize(String SourceString, int ChunkSize)
    {
        List<String> ChunkStringList;

        ChunkStringList = new ArrayList<>();
        for (int LoopI = 0; LoopI < SourceString.length(); LoopI += ChunkSize)
        {
            ChunkStringList.add(SourceString.substring(LoopI, Math.min(SourceString.length(), LoopI + ChunkSize)));
        }

        return ChunkStringList;
    }

    /**
     * Convert Dump Fingerprint Hexadecimal String to Bitmap Bytes Array
     * @param DumpFingerprintHexString
     * @return
     */
    private byte[] ConvertDumpFingerprintHexStringToBitmapBytesArray(String DumpFingerprintHexString)
    {
        List<String> DumpFingerprintChunkList;
        StringBuilder DumpFingerprintImgStringBuilder;

        byte[] BitmapHeaderDefinition = {
                0x42,0x4d,              // File Type
                // 0x36,0x6c,0x01,0x00, // File Size
                0x0,0x0,0x0,0x00,       // File Size
                0x00,0x00,              // Reserved
                0x00,0x00,              // Reserved
                0x36,0x4,0x00,0x00,     // Head Byte
                // Infoheader
                0x28,0x00,0x00,0x00,    // Struct Size

                // 0x00,0x01,0x00,0x00, // Bitmap Width
                0x00,0x00,0x0,0x00,     // Bitmap Width
                //0x68,0x01,0x00,0x00,  // Bitmap Height
                0x00,0x00,0x00,0x00,    // Bitmap Height

                0x01,0x00,              // Must be 1
                0x08,0x00,              // Color Count
                0x00,0x00,0x00,0x00,    // Compression
                // 0x00,0x68,0x01,0x00, // Data Size
                0x00,0x00,0x00,0x00,    // Data Size
                0x00,0x00,0x00,0x00,    // DPI X
                0x00,0x00,0x00,0x00,    // DPI Y
                0x00,0x00,0x00,0x00,    // Color Used
                0x00,0x00,0x00,0x00,    // Color Important
        };
        byte[] BitmapHeader = new byte[1078];
        byte[] BitmapContent = new byte[1078 + (FINGERPRINT_BITMAP_WIDTH * FINGERPRINT_BITMAP_HEIGHT)];
        byte[] DumpFingerprintBytesArrayTmp;

        String DumpFingerprintHexStringRaw;
        String DumpFingerprintHexStringNew;
        String DumpFingerprintHexStringTmp;

        int LoopI;
        int LoopJ;
        int LoopK;
        long BitmapSizeTemp;

        try
        {
            // region Initialize Bitmap Width, Height
            System.arraycopy(BitmapHeaderDefinition, 0, BitmapHeader, 0, BitmapHeaderDefinition.length);

            BitmapSizeTemp = FINGERPRINT_BITMAP_WIDTH;
            BitmapHeader[18]= (byte) (BitmapSizeTemp & 0xFF);
            BitmapSizeTemp = BitmapSizeTemp>>8;
            BitmapHeader[19]= (byte) (BitmapSizeTemp & 0xFF);
            BitmapSizeTemp = BitmapSizeTemp>>8;
            BitmapHeader[20]= (byte) (BitmapSizeTemp & 0xFF);
            BitmapSizeTemp = BitmapSizeTemp>>8;
            BitmapHeader[21]= (byte) (BitmapSizeTemp & 0xFF);

            BitmapSizeTemp = FINGERPRINT_BITMAP_HEIGHT;
            BitmapHeader[22]= (byte) (BitmapSizeTemp & 0xFF);
            BitmapSizeTemp = BitmapSizeTemp>>8;
            BitmapHeader[23]= (byte) (BitmapSizeTemp & 0xFF);
            BitmapSizeTemp = BitmapSizeTemp>>8;
            BitmapHeader[24]= (byte) (BitmapSizeTemp & 0xFF);
            BitmapSizeTemp = BitmapSizeTemp>>8;
            BitmapHeader[25]= (byte) (BitmapSizeTemp & 0xFF);

            LoopJ = 0;
            for (LoopI = 54; LoopI < 1078; LoopI = LoopI + 4)
            {
                BitmapHeader[LoopI] = BitmapHeader[LoopI + 1] = BitmapHeader[LoopI + 2] = (byte)LoopJ;
                BitmapHeader[LoopI + 3] = 0;
                LoopJ++;
            }
            // endregion

            DumpFingerprintHexStringRaw = DumpFingerprintHexString;
            DumpFingerprintHexStringNew = DumpFingerprintHexStringRaw.substring(24);
            DumpFingerprintChunkList = SplitStringByChunkSize(DumpFingerprintHexStringNew, 278);
            if (DumpFingerprintChunkList != null)
            {
                DumpFingerprintImgStringBuilder = new StringBuilder();
                for (LoopI = 0; LoopI < DumpFingerprintChunkList.size(); LoopI++)
                {
                    // During UART
                    DumpFingerprintHexStringTmp = DumpFingerprintChunkList.get(LoopI);
                    DumpFingerprintHexStringTmp = DumpFingerprintHexStringTmp.substring(18, DumpFingerprintHexStringTmp.length() - 4);
                    DumpFingerprintImgStringBuilder.append(DumpFingerprintHexStringTmp);
                }

                // Initialize Bitmap Content
                System.arraycopy(BitmapHeader, 0, BitmapContent, 0, BitmapHeader.length);
                DumpFingerprintHexStringNew = DumpFingerprintImgStringBuilder.toString();
                LoopJ = 1078;
                for (LoopI = 0; LoopI < DumpFingerprintHexStringNew.length(); LoopI++)
                {
                    // In order to cut down the communication time when uploading or downloading image through UART interface, only the upper 4 bits of the pixel
                    DumpFingerprintHexStringTmp = DumpFingerprintHexStringNew.charAt(LoopI) + "0";
                    DumpFingerprintBytesArrayTmp = ConvertHexStringToBytesArray(DumpFingerprintHexStringTmp);
                    if (DumpFingerprintBytesArrayTmp != null)
                    {
                        for (LoopK = 0; LoopK < DumpFingerprintBytesArrayTmp.length; LoopK++)
                        {
                            BitmapContent[LoopJ] = DumpFingerprintBytesArrayTmp[LoopK];
                            LoopJ++;
                        }
                    }
                }

                return BitmapContent;
            }

            return  null;
        }
        catch (Exception ex)
        {
            _MessageTextView.append("Convert Fingerprint to Bitmap Bytes Array Exception: " + ex.getMessage() + "\n");
            return  null;
        }
        finally
        {
            // Release Variable
            DumpFingerprintBytesArrayTmp = null;
            BitmapContent = null;
            BitmapHeader = null;
            BitmapHeaderDefinition = null;
            DumpFingerprintImgStringBuilder = null;
            DumpFingerprintChunkList = null;
        }
    }

    /**
     * Refresh Buttons Status
     */
    private void RefreshButtonsStatus()
    {
        _ClearButton.setEnabled(true);
        _OpenButton.setEnabled(!_IsUsbSerialPortConnected);
        _ScanButton.setEnabled(_IsUsbSerialPortConnected);
        _DumpButton.setEnabled(_IsUsbSerialPortConnected && _IsFingerprintScanned);
        _CloseButton.setEnabled(_IsUsbSerialPortConnected);
    }

    /**
     * Implement SerialInputOutputManager.onNewData Event
     * @param data
     */
    @Override
    public void onNewData(byte[] data)
    {
        runOnUiThread(
                () ->
                {
                    byte[] FingerprintBitmapBytesArray;
                    Bitmap FingerprintBitmap;

                    String HexCmdResponseACK = "EF01FFFFFFFF07000300000A";
                    String HexCmdResponseScanFingerprintNAK = "EF01FFFFFFFF07000302000C";
                    String HexCmdResponseDumpFingerprintNAK01 = "EF01FFFFFFFF07000301000A";
                    String HexCmdResponseDumpFingerprintNAK0F = "EF01FFFFFFFF0700030F000A";

                    String HexCmdResponseTemp;

                    switch (_UsbSerialPortCommand)
                    {
                        case "ScanFingerprint":
                            HexCmdResponseTemp = ConvertBytesArrayToHexString(data);
                            if (HexCmdResponseTemp.equals(HexCmdResponseACK))
                            {
                                _MessageTextView.append("Request Fingerprint Scanner Module (BCC-FINGERPT-01R) scanning successful!\n");
                                _IsFingerprintScanned = true;
                            }
                            else if (HexCmdResponseTemp.equals(HexCmdResponseScanFingerprintNAK))
                            {
                                _MessageTextView.append("Please put finger on Fingerprint Scanner Module (BCC-FINGERPT-01R) and press Scan button!\n");
                                _IsFingerprintScanned = false;
                            }
                            // Refresh Buttons Status
                            RefreshButtonsStatus();
                            break;

                        case "DumpFingerprint":
                            try
                            {
                                HexCmdResponseTemp = ConvertBytesArrayToHexString(data);
                                _DumpFingerprintRawStringBuilder.append(HexCmdResponseTemp);
                                if (HexCmdResponseTemp.contains(HexCmdResponseACK))
                                {
                                    _MessageTextView.append("Request Fingerprint Scanner Module (BCC-FINGERPT-01R) dumping successful!\n");
                                }
                                else if (HexCmdResponseTemp.contains(HexCmdResponseDumpFingerprintNAK01))
                                {
                                    _DumpFingerprintRawStringBuilder.setLength(0);
                                    _MessageTextView.append("Request Fingerprint Scanner Module (BCC-FINGERPT-01R) dumping failed! Exception: Receiving Packet Error!\n");
                                    // Refresh Buttons Status
                                    RefreshButtonsStatus();
                                }
                                else if (HexCmdResponseTemp.contains(HexCmdResponseDumpFingerprintNAK0F))
                                {
                                    _DumpFingerprintRawStringBuilder.setLength(0);
                                    _MessageTextView.append("Request Fingerprint Scanner Module (BCC-FINGERPT-01R) dumping failed! Exception: Can't transmit continue data packet!\n");
                                    // Refresh Buttons Status
                                    RefreshButtonsStatus();
                                }
                                else
                                {
                                    _MessageTextView.append("Receiving Packet: " + _DumpFingerprintRawStringBuilder.toString().length() + "\n");
                                    if (_DumpFingerprintRawStringBuilder.toString().length() == DUMP_FINGERPRINT_PACKET_LENGTH)
                                    {
                                        _MessageTextView.append("Complete Fingerprint Scanner Module (BCC-FINGERPT-01R) dumping packet!\n");

                                        FingerprintBitmapBytesArray = ConvertDumpFingerprintHexStringToBitmapBytesArray(_DumpFingerprintRawStringBuilder.toString());
                                        if (FingerprintBitmapBytesArray != null)
                                        {
                                            FingerprintBitmap = BitmapFactory.decodeByteArray(FingerprintBitmapBytesArray, 0 , FingerprintBitmapBytesArray.length);
                                            _FingerprintImageView.setImageBitmap(FingerprintBitmap);
                                        }

                                        // Refresh Buttons Status
                                        RefreshButtonsStatus();
                                    }
                                }
                            }
                            catch (Exception ex)
                            {
                                _MessageTextView.append("Dump Fingerprint Scanner Module (BCC-FINGERPT-01R) failed! Exception: " + ex.getMessage() + "\n");
                            }
                            finally
                            {
                                // Release Variable
                                FingerprintBitmapBytesArray = null;
                            }
                            break;

                        default:
                            break;
                    }
                });
    }

    /**
     * Implement SerialInputOutputManager.onRunError Event
     * @param e
     */
    @Override
    public void onRunError(Exception e)
    {
        runOnUiThread(
                () ->
                {
                    _MessageTextView.append("Fingerprint Scanner Module (BCC-FINGERPT-01R) Communication Exception: " + e.getMessage() + "\n");
                });
    }

    /**
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Variable
        _DumpFingerprintRawStringBuilder = new StringBuilder();

        // Initialize TextView
        _MessageTextView = (TextView)findViewById(R.id.MessageTextView);
        _MessageTextView.setMovementMethod(new ScrollingMovementMethod());

        // Intialization Button
        _ClearButton= (Button)findViewById(R.id.ClearButton);
        _ClearButton.setOnClickListener(MainActivity.this);
        _OpenButton = (Button)findViewById(R.id.OpenButton);
        _OpenButton.setOnClickListener(MainActivity.this);
        _ScanButton = (Button)findViewById(R.id.ScanButton);
        _ScanButton.setOnClickListener(MainActivity.this);
        _DumpButton = (Button)findViewById(R.id.DumpButton);
        _DumpButton.setOnClickListener(MainActivity.this);
        _CloseButton = (Button)findViewById(R.id.CloseButton);
        _CloseButton.setOnClickListener(MainActivity.this);
        _FingerprintImageView = (ImageView)findViewById(R.id.FingerprintImageView);
        // Refresh Buttons Status
        RefreshButtonsStatus();
    }

    /**
     * Implement Button.onClick Event
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v)
    {
        byte[] BytesCmdRequestScanFingerprint = ConvertHexStringToBytesArray("EF01FFFFFFFF010003010005");
        byte[] BytesCmdRequestDumpFingerprint = ConvertHexStringToBytesArray("EF01FFFFFFFF0100030A000E");

        switch (v.getId())
        {
            case R.id.ClearButton:
                _MessageTextView.setText("");
                _FingerprintImageView.setImageResource(android.R.color.transparent);
                break;

            case R.id.OpenButton:
                try
                {
                    _IsUsbSerialPortConnected = false;

                    // Find all available drivers from attached devices.
                    _UsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                    _UsbSerialDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(_UsbManager);
                    if (_UsbSerialDrivers.isEmpty())
                    {
                        _MessageTextView.append("Can't find Fingerprint Scanner Module (BCC-FINGERPT-01R)!\n");
                        return;
                    }

                    // Open a connection to the first available driver.
                    _UsbSerialDriver = _UsbSerialDrivers.get(0);
                    _UsbDeviceConnection = _UsbManager.openDevice(_UsbSerialDriver.getDevice());
                    if (_UsbDeviceConnection == null)
                    {
                        _MessageTextView.append("Request Fingerprint Scanner Module (BCC-FINGERPT-01R) permission!\n");
                        _UsbPermissionPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_ONE_SHOT);
                        _UsbManager.requestPermission(_UsbSerialDriver.getDevice(), _UsbPermissionPendingIntent);
                        return;
                    }

                    // Most devices have just one port (port 0)
                    _UsbSerialPort = _UsbSerialDriver.getPorts().get(0);
                    _UsbSerialPort.open(_UsbDeviceConnection);
                    _UsbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_NONE);

                    _SerialInputOutputManager = new SerialInputOutputManager(_UsbSerialPort, this);
                    _SerialInputOutputManager.start();

                    _MessageTextView.append("Open Fingerprint Scanner Module (BCC-FINGERPT-01R) successful!\n");

                    _IsUsbSerialPortConnected = true;
                }
                catch (Exception ex)
                {
                    _MessageTextView.append("Open Fingerprint Scanner Module (BCC-FINGERPT-01R) failed! Exception: " + ex.getMessage() + "\n");
                    _IsUsbSerialPortConnected = false;
                }
                finally
                {
                    // Refresh Buttons Status
                    RefreshButtonsStatus();
                }
                break;

            case R.id.ScanButton:
                try
                {
                    if (_IsUsbSerialPortConnected)
                    {
                        _UsbSerialPortCommand = "ScanFingerprint";
                        _IsFingerprintScanned = false;
                        _UsbSerialPort.write(BytesCmdRequestScanFingerprint, WRITE_WAIT_MILLIS);
                    }
                }
                catch (Exception ex)
                {
                    _MessageTextView.append("Request Fingerprint Scanner Module (BCC-FINGERPT-01R) scanning failed! Exception: " + ex.getMessage() + "\n");
                }
                break;

            case R.id.DumpButton:
                try
                {
                    if (_IsUsbSerialPortConnected)
                    {
                        if (_IsFingerprintScanned)
                        {
                            _ClearButton.setEnabled(false);
                            _OpenButton.setEnabled(false);
                            _ScanButton.setEnabled(false);
                            _DumpButton.setEnabled(false);
                            _CloseButton.setEnabled(false);
                            _FingerprintImageView.setImageResource(android.R.color.transparent);

                            _DumpFingerprintRawStringBuilder.setLength(0);
                            _UsbSerialPortCommand = "DumpFingerprint";
                            _UsbSerialPort.write(BytesCmdRequestDumpFingerprint, WRITE_WAIT_MILLIS);
                        }
                    }
                }
                catch (Exception ex)
                {
                    _MessageTextView.append("Request Fingerprint Scanner Module (BCC-FINGERPT-01R) dumping failed! Exception: " + ex.getMessage() + "\n");
                }
                break;

            case R.id.CloseButton:
                if (_IsUsbSerialPortConnected)
                {
                    _IsUsbSerialPortConnected = false;
                    try
                    {
                        if(_SerialInputOutputManager != null)
                        {
                            _SerialInputOutputManager.setListener(null);
                            _SerialInputOutputManager.stop();
                        }
                        _SerialInputOutputManager = null;
                        _UsbSerialPort.close();
                        _MessageTextView.append("Close Fingerprint Scanner Module (BCC-FINGERPT-01R) successful!\n");
                    }
                    catch (IOException ex)
                    {
                        _MessageTextView.append("Close Fingerprint Scanner Module (BCC-FINGERPT-01R) failed! Exception: " + ex.getMessage() + "\n");
                    }
                    finally
                    {
                        _UsbSerialPort = null;
                    }
                }
                // Refresh Buttons Status
                RefreshButtonsStatus();
                break;

            default:
                break;
        }
    }
    // endregion
}