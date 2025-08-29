package kr.hhplus.be.server.coupon.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

	List<Coupon> findAllByUserId(Long userId);

	Optional<Coupon> findByIdAndUserId(Long id, Long userId);

	@Query("SELECT c FROM Coupon c JOIN FETCH c.user")
	List<Coupon> findAllWithUser();

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Coupon c
			   SET c.used = true
			 WHERE c.id = :couponId
			   AND c.user.id = :userId
			   AND c.used = false
			""")
	int markCouponAsUsed(@Param("couponId") Long couponId, @Param("userId") Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Coupon c
			   SET c.used = false
			 WHERE c.id = :couponId
			   AND c.used = true
			""")
	int restoreCouponIfUsed(@Param("couponId") Long couponId);
}
