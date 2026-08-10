//package DuAnTrainning.AuctionSystem.mapper;
//
//import DuAnTrainning.AuctionSystem.dto.request.RegisterRequestDTO;
//import DuAnTrainning.AuctionSystem.entity.User;
//import org.mapstruct.Mapper;
//import org.mapstruct.Mapping;
//
//@Mapper(componentModel = "spring")
//public interface UserMapper {
//
//    @Mapping(target = "id", ignore = true)
//    @Mapping(target = "username", source = "requestDTO.username")
//    @Mapping(target = "email", source = "requestDTO.email")
//    @Mapping(target = "passwordHash", source = "encodedPassword")
//    @Mapping(target = "unpaidStrikeCount", constant = "0") // 👈 Set tường minh gậy vi phạm ban đầu = 0
//    @Mapping(target = "bannedUntil", ignore = true)
//    @Mapping(target = "role", constant = "USER")   // 👈 MapStruct tự map String "USER" -> UserRole.USER
//    @Mapping(target = "status", constant = "ACTIVE") // 👈 MapStruct tự map String "ACTIVE" -> UserStatus.ACTIVE
//    @Mapping(target = "createdAt", ignore = true)
//    User toEntity(RegisterRequestDTO requestDTO, String encodedPassword);
//}
