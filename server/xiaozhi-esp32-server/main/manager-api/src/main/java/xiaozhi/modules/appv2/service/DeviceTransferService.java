package xiaozhi.modules.appv2.service;

import java.util.List;

import xiaozhi.modules.appv2.dto.V2DeviceTransferRequest;
import xiaozhi.modules.appv2.dto.V2DeviceTransferResponse;

public interface DeviceTransferService {
    V2DeviceTransferResponse requestTransfer(String deviceId, V2DeviceTransferRequest request);

    V2DeviceTransferResponse acceptTransfer(Long transferId);

    V2DeviceTransferResponse cancelTransfer(Long transferId);

    List<V2DeviceTransferResponse> listPendingIncomingTransfers();
}
