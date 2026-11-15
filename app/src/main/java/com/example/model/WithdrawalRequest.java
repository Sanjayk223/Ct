package com.example.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

@IgnoreExtraProperties
public class WithdrawalRequest {
    @DocumentId
    public String id = "";
    public String userId = "";
    public String upiId = "";
    public long coinsRedeemed = 0L;
    public double amountInr = 0.0;
    public String status = "pending";
    public String referenceCode = "";
    @ServerTimestamp
    public Timestamp createdAt;

    public WithdrawalRequest() {}
}
