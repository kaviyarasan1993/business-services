package org.egov.demand.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.egov.demand.model.AuditDetails;
import org.egov.demand.model.BillAccountDetailV2;
import org.egov.demand.model.BillDetailV2;
import org.egov.demand.model.BillSearchCriteria;
import org.egov.demand.model.BillV2;
import org.egov.demand.repository.querybuilder.BillQueryBuilder;
import org.egov.demand.repository.rowmapper.BillRowMapperV2;
import org.egov.demand.util.Constants;
import org.egov.demand.web.contract.BillRequestV2;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class BillRepositoryV2 {

	@Autowired
	private BillQueryBuilder billQueryBuilder;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private BillRowMapperV2 searchBillRowMapper;
	
	@Autowired
	private ObjectMapper mapper;
	
	public List<BillV2> findBill(BillSearchCriteria billCriteria){
		
		List<Object> preparedStatementValues = new ArrayList<>();
		String queryStr = billQueryBuilder.getBillQuery(billCriteria, preparedStatementValues);
		log.debug("query:::"+queryStr+"  preparedStatementValues::"+preparedStatementValues);
		return jdbcTemplate.query(queryStr, preparedStatementValues.toArray(), searchBillRowMapper);
	}
	
	@Transactional
	public void saveBill(BillRequestV2 billRequest){
		
		List<BillV2> bills = billRequest.getBills();
		
		jdbcTemplate.batchUpdate(BillQueryBuilder.INSERT_BILL_QUERY, new BatchPreparedStatementSetter() {
			
			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				BillV2 bill = bills.get(index);

				AuditDetails auditDetails = bill.getAuditDetails();
				
				ps.setString(1, bill.getId());
				ps.setString(2, bill.getTenantId());
				ps.setString(3, bill.getPayerName());
				ps.setString(4, bill.getPayerAddress());
				ps.setString(5, bill.getPayerEmail());
				ps.setObject(6, null);
				ps.setObject(7, null);
				ps.setString(8, auditDetails.getCreatedBy());
				ps.setLong(9, auditDetails.getCreatedTime());
				ps.setString(10, auditDetails.getLastModifiedBy());
				ps.setLong(11, auditDetails.getLastModifiedTime());
				ps.setString(12, bill.getMobileNumber());
				ps.setString(13, bill.getStatus().toString());
				ps.setObject(14, getPGObject(bill.getAdditionalDetails()));
			}
			
			@Override
			public int getBatchSize() {
				return bills.size();
			}
		});
		saveBillDetails(billRequest);
	}
	
	public void saveBillDetails(BillRequestV2 billRequest) {

		List<BillV2> bills = billRequest.getBills();
		List<BillDetailV2> billDetails = new ArrayList<>();
		List<BillAccountDetailV2> billAccountDetails = new ArrayList<>();
		AuditDetails auditDetails = bills.get(0).getAuditDetails();
		
		Map<String, BillV2> billDetailIdAndBillMap = new HashMap<>();

		for (BillV2 bill : bills) {
			
			List<BillDetailV2> tempBillDetails = bill.getBillDetails();
			billDetails.addAll(tempBillDetails);

			for (BillDetailV2 billDetail : tempBillDetails) {
				
				billDetailIdAndBillMap.put(billDetail.getId(), bill);
				billAccountDetails.addAll(billDetail.getBillAccountDetails());
			}
		}

		jdbcTemplate.batchUpdate(BillQueryBuilder.INSERT_BILLDETAILS_QUERY, new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				BillDetailV2 billDetail = billDetails.get(index);
				BillV2 bill = billDetailIdAndBillMap.get(billDetail.getId());

				ps.setString(1, billDetail.getId());
				ps.setString(2, billDetail.getTenantId());
				ps.setString(3, billDetail.getBillId());
				ps.setString(4, billDetail.getDemandId());
				ps.setLong(5, billDetail.getFromPeriod());
				ps.setLong(6, billDetail.getToPeriod());
				ps.setString(7, bill.getBusinessService());
				ps.setString(8, bill.getBillNumber());
				ps.setLong(9, bill.getBillDate());
				ps.setString(10, bill.getConsumerCode());
				ps.setString(11, null);
				ps.setString(12, null);
				ps.setString(13, null);
				ps.setObject(14, null);
				ps.setObject(15, billDetail.getAmount());
				// apportioning logic does not reside in billing service anymore 
				ps.setBoolean(16, false);
				ps.setObject(17, bill.getPartPaymentAllowed());
				
				String collectionModesNotAllowed = null != bill.getCollectionModesNotAllowed()
						? StringUtils.join(bill.getCollectionModesNotAllowed(), ",")
						: null;
				ps.setString(18, collectionModesNotAllowed);
				
				ps.setString(19, auditDetails.getCreatedBy());
				ps.setLong(20, auditDetails.getCreatedTime());
				ps.setString(21, auditDetails.getLastModifiedBy());
				ps.setLong(22, auditDetails.getLastModifiedTime());
				ps.setBoolean(23, bill.getIsAdvanceAllowed());
				ps.setLong(24, billDetail.getExpiryDate());
			}

			@Override
			public int getBatchSize() {
				return billDetails.size();
			}
		});

		saveBillAccountDetail(billAccountDetails, auditDetails);
	}

	public void saveBillAccountDetail(List<BillAccountDetailV2> billAccountDetails, AuditDetails auditDetails) {

		jdbcTemplate.batchUpdate(BillQueryBuilder.INSERT_BILLACCOUNTDETAILS_QUERY, new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				BillAccountDetailV2 billAccountDetail = billAccountDetails.get(index);

				ps.setString(1, billAccountDetail.getId());
				ps.setString(2, billAccountDetail.getTenantId());
				ps.setString(3, billAccountDetail.getBillDetailId());
				ps.setString(4, billAccountDetail.getDemandDetailId());
				ps.setObject(5, billAccountDetail.getOrder());
				ps.setBigDecimal(6, billAccountDetail.getAmount());
				ps.setObject(7, billAccountDetail.getAdjustedAmount());
				ps.setObject(8, null);
				ps.setString(9, null);
				ps.setString(10, auditDetails.getCreatedBy());
				ps.setLong(11, auditDetails.getCreatedTime());
				ps.setString(12, auditDetails.getLastModifiedBy());
				ps.setLong(13, auditDetails.getLastModifiedTime());
				ps.setString(14, billAccountDetail.getTaxHeadCode());
			}

			@Override
			public int getBatchSize() {
				return billAccountDetails.size();
			}
		});
	}

	
	/*
	 * Utility methods
	 */
	/**
	 * converts the object to a pgObject for persistence
	 * 
	 * @param additionalDetails
	 * @return
	 */
	private PGobject getPGObject(Object additionalDetails) {

		String value = null;
		try {
			value = mapper.writeValueAsString(additionalDetails);
		} catch (JsonProcessingException e) {
			throw new CustomException(Constants.EG_BS_JSON_EXCEPTION_KEY, Constants.EG_BS_JSON_EXCEPTION_MSG);
		}

		PGobject json = new PGobject();
		json.setType(Constants.DB_TYPE_JSONB);
		try {
			json.setValue(value);
		} catch (SQLException e) {
			throw new CustomException(Constants.EG_BS_JSON_EXCEPTION_KEY, Constants.EG_BS_JSON_EXCEPTION_MSG);
		}
		return json;
	}
}
