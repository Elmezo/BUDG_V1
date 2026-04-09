-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 02:23 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `legal_audit_history`
--

CREATE TABLE `legal_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `legal_audit_history`
--

INSERT INTO `legal_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(1, 52, 'Legal', 'Details', 'Updated', 'Long Name', 'dasdasd', 'dasdasd11111', 'Alice Smith', '2025-10-23 16:13:51', '2025-10-23 16:13:51'),
(9, 18, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'sdas', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(9, 19, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'dasfa', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(9, 22, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(9, 24, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(9, 26, 'Legal', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(10, 20, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'sdas', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(10, 21, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'dasfa', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(10, 23, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(10, 25, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(10, 27, 'Legal', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:04:51', '2025-10-23 13:04:51'),
(11, 28, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'dfgdfg', 'esraa Johnson', '2025-10-23 13:11:12', '2025-10-23 13:11:12'),
(11, 29, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'dfgdfgdf', 'esraa Johnson', '2025-10-23 13:11:12', '2025-10-23 13:11:12'),
(11, 30, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:11:12', '2025-10-23 13:11:12'),
(11, 31, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'esraa Johnson', '2025-10-23 13:11:12', '2025-10-23 13:11:12'),
(11, 32, 'Legal', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:11:12', '2025-10-23 13:11:12'),
(12, 34, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'hghf', 'esraa Johnson', '2025-10-23 13:21:23', '2025-10-23 13:21:23'),
(12, 35, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'dfyhud', 'esraa Johnson', '2025-10-23 13:21:23', '2025-10-23 13:21:23'),
(12, 36, 'Legal', 'Details', 'Added', 'Description', NULL, 'sdfgsdf', 'esraa Johnson', '2025-10-23 13:21:23', '2025-10-23 13:21:23'),
(12, 37, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:21:24', '2025-10-23 13:21:24'),
(12, 38, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'esraa Johnson', '2025-10-23 13:21:24', '2025-10-23 13:21:24'),
(12, 39, 'Legal', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:21:24', '2025-10-23 13:21:24'),
(12, 40, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'esraa Johnson', '2025-10-23 13:21:24', '2025-10-23 13:21:24'),
(12, 41, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:21:24', '2025-10-23 13:21:24'),
(12, 42, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:21:24', '2025-10-23 13:21:24'),
(13, 43, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'jklk', 'esraa Johnson', '2025-10-23 13:54:16', '2025-10-23 13:54:16'),
(13, 44, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'nk.;j;ljl', 'esraa Johnson', '2025-10-23 13:54:16', '2025-10-23 13:54:16'),
(13, 45, 'Legal', 'Details', 'Added', 'Description', NULL, 'jkljk', 'esraa Johnson', '2025-10-23 13:54:16', '2025-10-23 13:54:16'),
(13, 46, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:54:16', '2025-10-23 13:54:16'),
(13, 47, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'esraa Johnson', '2025-10-23 13:54:17', '2025-10-23 13:54:17'),
(13, 48, 'Legal', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:54:17', '2025-10-23 13:54:17'),
(13, 49, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'esraa Johnson', '2025-10-23 13:54:17', '2025-10-23 13:54:17'),
(13, 50, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 13:54:17', '2025-10-23 13:54:17'),
(13, 51, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 13:54:17', '2025-10-23 13:54:17'),
(14, 53, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'fgjhgfj', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 54, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'jghj', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 55, 'Legal', 'Details', 'Added', 'Description', NULL, 'fg', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 56, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 57, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 58, 'Legal', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 59, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 60, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 61, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:42:43', '2025-10-23 16:42:43'),
(14, 75, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-02 10:40:28', '2025-11-02 10:40:28'),
(14, 76, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Legal Entity Owner', NULL, 'System', '2025-11-02 10:40:28', '2025-11-02 10:40:28'),
(14, 77, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-02 10:40:28', '2025-11-02 10:40:28'),
(15, 62, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'dfsfsdf', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 63, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'sefesfsef', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 64, 'Legal', 'Details', 'Added', 'Description', NULL, 'sdfsdf', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 65, 'Legal', 'Details', 'Added', 'Parent Legal Entity', NULL, 'sdas', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 66, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 67, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 68, 'Legal', 'Details', 'Added', 'Created By', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 69, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 70, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 71, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'esraa Johnson', '2025-10-23 16:57:58', '2025-10-23 16:57:58'),
(15, 72, 'Legal', 'Details', 'Status Change', 'Status', 'Active', 'Complleted', 'esraa Johnson', '2025-10-23 16:58:15', '2025-10-23 16:58:15'),
(15, 73, 'Legal', 'Details', 'Updated', 'Long Name', 'sefesfsef', 'sefesfsef111', 'Alice Smith', '2025-10-27 10:34:04', '2025-10-27 10:34:04'),
(15, 74, 'Legal', 'Details', 'Updated', 'Description', 'sdfsdf', 'abdo', 'Alice Smith', '2025-10-27 10:34:42', '2025-10-27 10:34:42'),
(15, 78, 'Stakeholder', 'delete', 'Deleted', 'Name', 'esraa Johnson', NULL, 'System', '2025-11-02 11:09:31', '2025-11-02 11:09:31'),
(15, 79, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Legal Entity Owner', NULL, 'System', '2025-11-02 11:09:31', '2025-11-02 11:09:31'),
(15, 80, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-02 11:09:31', '2025-11-02 11:09:31'),
(15, 81, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Diana Wilson', 'System', '2025-11-02 11:09:45', '2025-11-02 11:09:45'),
(15, 82, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Legal Entity Owner', 'System', '2025-11-02 11:09:45', '2025-11-02 11:09:45'),
(15, 83, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-02 11:09:45', '2025-11-02 11:09:45'),
(16, 84, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'لاتناتل', 'Alice Smith', '2025-11-02 11:19:29', '2025-11-02 11:19:29'),
(16, 85, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'تنانلاتن', 'Alice Smith', '2025-11-02 11:19:29', '2025-11-02 11:19:29'),
(16, 86, 'Legal', 'Details', 'Added', 'Description', NULL, 'تنلاتنلا', 'Alice Smith', '2025-11-02 11:19:29', '2025-11-02 11:19:29'),
(16, 87, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-02 11:19:29', '2025-11-02 11:19:29'),
(16, 88, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-02 11:19:29', '2025-11-02 11:19:29'),
(16, 89, 'Legal', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-02 11:19:29', '2025-11-02 11:19:29'),
(17, 90, 'Legal', 'Details', 'Added', 'Short Name', NULL, 'shorttt', 'Alice Smith', '2025-11-09 13:43:23', '2025-11-09 13:43:23'),
(17, 91, 'Legal', 'Details', 'Added', 'Long Name', NULL, 'legalEntity4', 'Alice Smith', '2025-11-09 13:43:23', '2025-11-09 13:43:23'),
(17, 92, 'Legal', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 13:43:23', '2025-11-09 13:43:23'),
(17, 93, 'Legal', 'Details', 'Added', 'Parent Legal Entity', NULL, 'SHORT', 'Alice Smith', '2025-11-09 13:43:23', '2025-11-09 13:43:23'),
(17, 94, 'Legal', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:43:23', '2025-11-09 13:43:23'),
(17, 95, 'Legal', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-09 13:43:23', '2025-11-09 13:43:23'),
(17, 96, 'Legal', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:43:23', '2025-11-09 13:43:23'),
(17, 97, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 13:43:56', '2025-11-09 13:43:56'),
(17, 98, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Legal Entity Owner', NULL, 'System', '2025-11-09 13:43:56', '2025-11-09 13:43:56'),
(17, 99, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-09 13:43:56', '2025-11-09 13:43:56'),
(17, 100, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Diana Wilson', 'System', '2025-11-09 13:43:56', '2025-11-09 13:43:56'),
(17, 101, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Legal Entity Owner', 'System', '2025-11-09 13:43:56', '2025-11-09 13:43:56'),
(17, 102, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-09 13:43:56', '2025-11-09 13:43:56'),
(17, 103, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Legal Entity Owner', 'Alice Smith', '2025-11-17 14:24:13', '2025-11-17 14:24:13'),
(17, 104, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-17 14:24:13', '2025-11-17 14:24:13'),
(17, 105, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'Alice Smith', '2025-11-17 14:24:13', '2025-11-17 14:24:13'),
(17, 106, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Legal Entity Owner', 'Alice Smith', '2025-11-17 14:28:35', '2025-11-17 14:28:35'),
(17, 107, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-17 14:28:35', '2025-11-17 14:28:35'),
(17, 108, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'Alice Smith', '2025-11-17 14:28:35', '2025-11-17 14:28:35'),
(17, 109, 'Stakeholder', 'delete', 'Deleted', 'Name', 'esraa Johnson', NULL, 'System', '2025-11-17 14:29:43', '2025-11-17 14:29:43'),
(17, 110, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Legal Entity Owner', NULL, 'System', '2025-11-17 14:29:43', '2025-11-17 14:29:43'),
(17, 111, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-17 14:29:43', '2025-11-17 14:29:43'),
(17, 112, 'Stakeholder', 'delete', 'Deleted', 'Name', 'esraa Johnson', NULL, 'System', '2025-11-17 14:29:43', '2025-11-17 14:29:43'),
(17, 113, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Legal Entity Owner', NULL, 'System', '2025-11-17 14:29:43', '2025-11-17 14:29:43'),
(17, 114, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-17 14:29:43', '2025-11-17 14:29:43'),
(17, 115, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Legal Entity Owner', 'Alice Smith', '2025-11-17 14:30:05', '2025-11-17 14:30:05'),
(17, 116, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-17 14:30:05', '2025-11-17 14:30:05'),
(17, 117, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'Alice Smith', '2025-11-17 14:30:05', '2025-11-17 14:30:05'),
(17, 118, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Legal Entity Owner', 'Alice Smith', '2025-11-17 15:16:17', '2025-11-17 15:16:17'),
(17, 119, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-17 15:16:17', '2025-11-17 15:16:17'),
(17, 120, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'esraa Johnson', 'Alice Smith', '2025-11-17 15:16:17', '2025-11-17 15:16:17');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `legal_audit_history`
--
ALTER TABLE `legal_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `legal_audit_history`
--
ALTER TABLE `legal_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=121;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `legal_audit_history`
--
ALTER TABLE `legal_audit_history`
  ADD CONSTRAINT `legal_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `legal` (`ID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
