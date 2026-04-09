-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 01:46 PM
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
-- Table structure for table `capability_audit_history`
--

CREATE TABLE `capability_audit_history` (
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
-- Dumping data for table `capability_audit_history`
--

INSERT INTO `capability_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(10, 18, 'Capability', 'Details', 'Added', 'Primary Name', NULL, 'sdfsdf', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 19, 'Capability', 'Details', 'Added', 'Description', NULL, 'sdfsd', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 20, 'Capability', 'Details', 'Added', 'Reference Number', NULL, 'sdfs', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 21, 'Capability', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 22, 'Capability', 'Details', 'Added', 'Lifecycle', NULL, 'lfc ', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 23, 'Capability', 'Details', 'Added', 'Capability Type', NULL, 'cap type', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 24, 'Capability', 'Details', 'Added', 'Classification', NULL, 'cap1', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 25, 'Capability', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(10, 26, 'Capability', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 13:27:12', '2025-10-23 13:27:12'),
(11, 27, 'Capability', 'Details', 'Added', 'Primary Name', NULL, 'sdfgfg', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 28, 'Capability', 'Details', 'Added', 'Description', NULL, 'dfgdfg', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 29, 'Capability', 'Details', 'Added', 'Reference Number', NULL, 'dfgdfg', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 30, 'Capability', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 31, 'Capability', 'Details', 'Added', 'Lifecycle', NULL, 'lfc ', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 32, 'Capability', 'Details', 'Added', 'Capability Type', NULL, 'cap type', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 33, 'Capability', 'Details', 'Added', 'Classification', NULL, 'cap1', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 34, 'Capability', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 35, 'Capability', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 36, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 37, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(11, 38, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 13:31:34', '2025-10-23 13:31:34'),
(12, 39, 'Capability', 'Details', 'Added', 'Primary Name', NULL, 'khjk', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 40, 'Capability', 'Details', 'Added', 'Description', NULL, 'jkgh', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 41, 'Capability', 'Details', 'Added', 'Reference Number', NULL, 'hk', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 42, 'Capability', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 43, 'Capability', 'Details', 'Added', 'Lifecycle', NULL, 'lfc ', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 44, 'Capability', 'Details', 'Added', 'Capability Type', NULL, 'cap type', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 45, 'Capability', 'Details', 'Added', 'Classification', NULL, 'cap1', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 46, 'Capability', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 47, 'Capability', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 48, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 49, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(12, 50, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:40:07', '2025-10-23 16:40:07'),
(13, 51, 'Capability', 'Details', 'Added', 'Primary Name', NULL, 'لتاتبت', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 52, 'Capability', 'Details', 'Added', 'Description', NULL, 'تا', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 53, 'Capability', 'Details', 'Added', 'Reference Number', NULL, 'تبل', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 54, 'Capability', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 55, 'Capability', 'Details', 'Added', 'Lifecycle', NULL, 'lfc ', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 56, 'Capability', 'Details', 'Added', 'Capability Type', NULL, 'cap type', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 57, 'Capability', 'Details', 'Added', 'Classification', NULL, 'cap1', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 58, 'Capability', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 59, 'Capability', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 60, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 61, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(13, 62, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 16:57:04', '2025-10-26 16:57:04'),
(14, 63, 'Capability', 'Details', 'Added', 'Primary Name', NULL, 'tfhdfh', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 64, 'Capability', 'Details', 'Added', 'Description', NULL, 'hfghdfghdfg', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 65, 'Capability', 'Details', 'Added', 'Reference Number', NULL, 'fgdhfgh', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 66, 'Capability', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 67, 'Capability', 'Details', 'Added', 'Lifecycle', NULL, 'lfc ', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 68, 'Capability', 'Details', 'Added', 'Capability Type', NULL, 'cap type', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 69, 'Capability', 'Details', 'Added', 'Classification', NULL, 'cap1', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 70, 'Capability', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 71, 'Capability', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 72, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 73, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 74, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 16:28:22', '2025-10-27 16:28:22'),
(14, 75, 'Capability', 'Details', 'Updated', 'Primary Name', 'tfhdfh123', 'tfhdfh', 'Alice Smith', '2025-10-27 17:04:43', '2025-10-27 17:04:43'),
(14, 76, 'Stakeholder', 'edit', 'Updated', 'Name', 'Alice Smith', 'Charlie Brown', 'System', '2025-10-30 16:55:30', '2025-10-30 16:55:30'),
(15, 77, 'Capability', 'Details', 'Added', 'Primary Name', NULL, 'capability test4', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 78, 'Capability', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 79, 'Capability', 'Details', 'Added', 'Parent Capability', NULL, 'capability test3', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 80, 'Capability', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 81, 'Capability', 'Details', 'Added', 'Lifecycle', NULL, 'lfc ', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 82, 'Capability', 'Details', 'Added', 'Capability Type', NULL, 'cap type', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 83, 'Capability', 'Details', 'Added', 'Classification', NULL, 'cap1', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 84, 'Capability', 'Details', 'Added', 'Is Public', NULL, 'Internal', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 85, 'Capability', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 86, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 87, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 88, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:39:30', '2025-11-09 13:39:30'),
(15, 89, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 13:40:15', '2025-11-09 13:40:15'),
(15, 90, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Capability Owner', NULL, 'System', '2025-11-09 13:40:15', '2025-11-09 13:40:15'),
(15, 91, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-09 13:40:15', '2025-11-09 13:40:15'),
(15, 92, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Charlie Brown', 'System', '2025-11-09 13:40:15', '2025-11-09 13:40:15'),
(15, 93, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Capability Owner', 'System', '2025-11-09 13:40:15', '2025-11-09 13:40:15'),
(15, 94, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-09 13:40:15', '2025-11-09 13:40:15'),
(16, 95, 'Capability', 'Details', 'Added', 'Primary Name', NULL, 'gegfg', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 96, 'Capability', 'Details', 'Added', 'Description', NULL, 'fff', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 97, 'Capability', 'Details', 'Added', 'Parent Capability', NULL, 'cap10', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 98, 'Capability', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 99, 'Capability', 'Details', 'Added', 'Lifecycle', NULL, 'lfc ', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 100, 'Capability', 'Details', 'Added', 'Capability Type', NULL, 'cap type', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 101, 'Capability', 'Details', 'Added', 'Classification', NULL, 'cap1', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 102, 'Capability', 'Details', 'Added', 'Is Public', NULL, 'Confidential', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 103, 'Capability', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 104, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 105, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28'),
(16, 106, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-13 13:50:28', '2025-11-13 13:50:28');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `capability_audit_history`
--
ALTER TABLE `capability_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `capability_audit_history`
--
ALTER TABLE `capability_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=107;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `capability_audit_history`
--
ALTER TABLE `capability_audit_history`
  ADD CONSTRAINT `capability_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `capability` (`ID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
