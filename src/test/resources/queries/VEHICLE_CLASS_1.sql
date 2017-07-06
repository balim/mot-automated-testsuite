select veh.registration, veh.vin, mtc.odometer_value
from vehicle veh, model_detail md,
  (select max(id) as id, vehicle_id  from mot_test_current
   group by vehicle_id
   limit 1000) as latest_mot,
   mot_test_current mtc
where veh.model_detail_id = md.id
and md.vehicle_class_id = 1 -- class 1 only
and veh.id = latest_mot.vehicle_id
and mtc.id = latest_mot.id
and mtc.status_id not in (4,5) -- exclude vehicles whose latest status is under test or failed
and odometer_result_type = 'OK'
and veh.registration not like "%-%" -- exclude dodgy test data on ACPT
and veh.registration not in (
  select registration
  from vehicle
  group by registration
  having count(registration) > 1 -- exclude where same registration has been entered as different vehicles
)
and veh.vin not in (
  select vin
  from vehicle
  group by vin
  having count(vin) > 1 -- exclude where same vin has been entered as different vehicles
)
limit 100