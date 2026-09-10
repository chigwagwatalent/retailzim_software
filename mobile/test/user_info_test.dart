import 'package:flutter_test/flutter_test.dart';
import 'package:retailzw_mobile/models/models.dart';

void main() {
  test('login branch name survives offline profile serialization', () {
    final login = LoginResponse.fromJson(
        {'userId': 1, 'branchId': 5, 'branchName': '  City Centre  '});
    expect(login.user.branchName, 'City Centre');
    expect(UserInfo.fromJson(login.user.toJson()).branchName, 'City Centre');
    expect(UserInfo.fromJson({'branchId': 5}).branchName, isEmpty);
  });
}
